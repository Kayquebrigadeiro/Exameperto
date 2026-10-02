package br.com.exameperto.identity;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class RepresentationService {
    private final JdbcTemplate jdbc;
    private final DataProtector protector;
    private final EmailGateway email;
    private final Duration invitationTtl;
    private final Duration maximumGrantTtl;
    private final com.github.benmanes.caffeine.cache.Cache<UUID,java.util.concurrent.atomic.AtomicInteger> invitationAttempts =
        com.github.benmanes.caffeine.cache.Caffeine.newBuilder().maximumSize(10000).expireAfterWrite(Duration.ofMinutes(1)).build();

    RepresentationService(JdbcTemplate jdbc, DataProtector protector, EmailGateway email,
        @Value("${family.invitation-ttl:PT48H}") Duration invitationTtl,
        @Value("${family.maximum-grant-ttl:P90D}") Duration maximumGrantTtl) {
        this.jdbc=jdbc; this.protector=protector; this.email=email;
        this.invitationTtl=invitationTtl; this.maximumGrantTtl=maximumGrantTtl;
    }

    @Transactional
    PatientView createPatient(UUID userId, PatientInput input) {
        requireActiveUser(userId);
        UUID id=UUID.randomUUID();
        try {
            jdbc.update("INSERT INTO paciente(id,usuario_id,cpf_cifrado,cpf_busca,nascimento,identidade_estado) VALUES (?,?,?,?,?,'PENDENTE')",
                id,userId,protector.encrypt(input.cpf()),protector.lookup("cpf:"+input.cpf()),Date.valueOf(input.birthDate()));
        } catch (DataIntegrityViolationException ex) { throw conflict("PATIENT_CONFLICT","Não foi possível criar o perfil de paciente."); }
        return new PatientView(id,"PENDENTE",0);
    }

    PatientView ownPatient(UUID userId) { return patientByOwner(userId,false); }

    @Transactional
    PatientView updatePatient(UUID userId, PatientInput input, long expectedVersion) {
        Map<String,Object> patient=patientRowByOwner(userId,true);
        if (number(patient,"version")!=expectedVersion) throw precondition();
        try {
            if (jdbc.update("UPDATE paciente SET cpf_cifrado=?,cpf_busca=?,nascimento=?,identidade_estado='PENDENTE',verificado_em=NULL,version=version+1,updated_at=clock_timestamp() WHERE id=? AND version=?",
                protector.encrypt(input.cpf()),protector.lookup("cpf:"+input.cpf()),Date.valueOf(input.birthDate()),patient.get("id"),expectedVersion)!=1) throw precondition();
        } catch (DataIntegrityViolationException ex) { throw conflict("PATIENT_CONFLICT","Não foi possível atualizar o perfil de paciente."); }
        return patient((UUID)patient.get("id"));
    }

    @Transactional
    PatientView accessiblePatient(UUID userId, UUID patientId, FamilyScope scope) {
        try {
            if (jdbc.queryForObject("SELECT count(*) FROM paciente WHERE id=? AND usuario_id=?",Long.class,patientId,userId)>0) {
                return jdbc.queryForObject("SELECT id,identidade_estado,version FROM paciente WHERE id=?",
                    (rs,n)->new PatientView(rs.getObject("id",UUID.class),rs.getString("identidade_estado"),rs.getLong("version")),patientId);
            }
            if (scope==null) throw notFound();
            jdbc.queryForObject("""
                SELECT a.id FROM autorizacao_paciente a JOIN autorizacao_escopo e ON e.autorizacao_id=a.id
                WHERE a.paciente_id=? AND a.familiar_id=? AND a.revogada_em IS NULL
                  AND a.expira_em>clock_timestamp() AND e.escopo=? FOR SHARE OF a
                """,UUID.class,patientId,userId,scope.name());
            return patient(patientId);
        } catch (EmptyResultDataAccessException ex) { throw notFound(); }
    }

    @Transactional
    FamilyInvitationView invite(UUID userId, UUID patientId, FamilyInvitationInput input) {
        if (!email.configured() || !protector.configured()) throw unavailable();
        if (invitationAttempts.get(userId,k->new java.util.concurrent.atomic.AtomicInteger()).incrementAndGet()>5)
            throw new RepresentationException(HttpStatus.TOO_MANY_REQUESTS,"RATE_LIMITED","Tente novamente mais tarde.",true);
        Map<String,Object> patient=patientRow(patientId,true);
        if (!userId.equals(patient.get("usuario_id"))) throw notFound();
        LocalDate birth=((Date)patient.get("nascimento")).toLocalDate();
        if (birth.isAfter(LocalDate.now().minus(Period.ofYears(18))))
            throw new RepresentationException(HttpStatus.UNPROCESSABLE_ENTITY,"OUT_OF_SCOPE","Este fluxo atende somente paciente adulto.",false);
        byte[] recipient=protector.lookup(AuthService.normalize(input.recipientEmail()));
        byte[] owner=jdbc.queryForObject("SELECT email_busca FROM usuario WHERE id=?",byte[].class,userId);
        if (java.security.MessageDigest.isEqual(recipient,owner)) throw conflict("SELF_GRANT","A própria conta não pode ser convidada.");
        UUID id=UUID.randomUUID(); String token=protector.token(); Instant expiry=Instant.now().plus(invitationTtl);
        jdbc.update("INSERT INTO convite_familiar(id,paciente_id,destinatario_email_busca,token_hash,estado,expira_em) VALUES (?,?,?,?,'PENDENTE',?)",
            id,patientId,recipient,protector.tokenHash(token),Timestamp.from(expiry));
        insertScopes("convite_familiar_escopo","convite_id",id,input.scopes());
        try {
            email.send(AuthService.normalize(input.recipientEmail()),"Convite privado do Exame Perto",
                "Entre em sua própria conta. Identificador do convite: "+id+" Código de uso único: "+token);
        } catch (RuntimeException ex) { throw unavailable(); }
        return invitation(id);
    }

    List<FamilyInvitationView> listInvitations(UUID userId, UUID patientId) {
        requirePatientOwner(userId,patientId);
        return jdbc.queryForList("SELECT id FROM convite_familiar WHERE paciente_id=? ORDER BY created_at DESC,id DESC",UUID.class,patientId)
            .stream().map(this::invitation).toList();
    }

    List<FamilyInvitationView> receivedInvitations(UUID userId) {
        byte[] emailLookup=jdbc.queryForObject("SELECT email_busca FROM usuario WHERE id=? AND estado='ATIVO'",byte[].class,userId);
        return jdbc.queryForList("SELECT id FROM convite_familiar WHERE destinatario_email_busca=? ORDER BY created_at DESC,id DESC",UUID.class,emailLookup)
            .stream().map(this::invitation).toList();
    }

    FamilyInvitationView getInvitation(UUID userId, UUID invitationId) {
        Map<String,Object> row=invitationRow(invitationId,false);
        if (!mayReadInvitation(userId,row)) throw notFound();
        return invitation(invitationId);
    }

    @Transactional
    FamilyInvitationView accept(UUID userId, UUID invitationId, AcceptInvitationInput input, long expectedVersion) {
        Map<String,Object> row=invitationRow(invitationId,true);
        if (number(row,"version")!=expectedVersion) throw precondition();
        byte[] accountEmail;
        try { accountEmail=jdbc.queryForObject("SELECT email_busca FROM usuario WHERE id=? AND estado='ATIVO' AND email_verificado_em IS NOT NULL FOR UPDATE",byte[].class,userId); }
        catch (EmptyResultDataAccessException ex) { throw notFound(); }
        if (!java.security.MessageDigest.isEqual(accountEmail,(byte[])row.get("destinatario_email_busca")) ||
            !java.security.MessageDigest.isEqual(protector.tokenHash(input.token()),(byte[])row.get("token_hash"))) throw notFound();
        if (!"PENDENTE".equals(row.get("estado")) || row.get("consumido_em")!=null)
            throw conflict("INVITATION_USED","O convite não está disponível.");
        if (!instant(row.get("expira_em")).isAfter(Instant.now())) throw conflict("INVITATION_EXPIRED","O convite expirou.");
        if (jdbc.update("UPDATE convite_familiar SET estado='ACEITO',consumido_em=clock_timestamp(),aceito_por=?,aceito_em=clock_timestamp(),version=version+1,updated_at=clock_timestamp() WHERE id=? AND version=? AND estado='PENDENTE' AND consumido_em IS NULL AND expira_em>clock_timestamp()",
            userId,invitationId,expectedVersion)!=1) throw conflict("INVITATION_USED","O convite não está disponível.");
        return invitation(invitationId);
    }

    @Transactional
    GrantView confirm(UUID userId, UUID patientId, ConfirmGrantInput input) {
        Map<String,Object> patient=patientRow(patientId,true);
        if (!userId.equals(patient.get("usuario_id"))) throw notFound();
        String password=jdbc.queryForObject("SELECT senha_hash FROM usuario WHERE id=? FOR UPDATE",String.class,userId);
        if (!Passwords.matches(input.password(),password)) throw AuthService.unauthorized();
        Map<String,Object> invitation=invitationRow(input.invitationId(),true);
        if (!patientId.equals(invitation.get("paciente_id"))) throw notFound();
        if (!"ACEITO".equals(invitation.get("estado")) || invitation.get("aceito_por")==null)
            throw conflict("INVITATION_NOT_ACCEPTED","O convite ainda não pode ser confirmado.");
        if (!instant(invitation.get("expira_em")).isAfter(Instant.now())) throw conflict("INVITATION_EXPIRED","O convite expirou.");
        Set<FamilyScope> invited=scopes("convite_familiar_escopo","convite_id",input.invitationId());
        if (!invited.equals(input.scopes())) throw conflict("SCOPE_MISMATCH","Os escopos devem coincidir com o convite aceito.");
        Instant now=Instant.now();
        if (!input.expiresAt().isAfter(now) || input.expiresAt().isAfter(now.plus(maximumGrantTtl)))
            throw new RepresentationException(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_EXPIRY","Informe uma expiração futura dentro do limite configurado.",false);
        UUID family=(UUID)invitation.get("aceito_por");
        jdbc.update("UPDATE autorizacao_paciente SET revogada_em=coalesce(revogada_em,clock_timestamp()),version=version+1,updated_at=clock_timestamp() WHERE paciente_id=? AND familiar_id=? AND revogada_em IS NULL AND expira_em<=clock_timestamp()",patientId,family);
        if (jdbc.queryForObject("SELECT count(*) FROM autorizacao_paciente WHERE paciente_id=? AND familiar_id=? AND revogada_em IS NULL",Long.class,patientId,family)>0)
            throw conflict("ACTIVE_GRANT_EXISTS","Já existe autorização ativa para esta conta.");
        UUID grant=UUID.randomUUID();
        try {
            jdbc.update("INSERT INTO autorizacao_paciente(id,paciente_id,familiar_id,concedida_por,convite_id,confirmada_em,expira_em) VALUES (?,?,?,?,?,clock_timestamp(),?)",
                grant,patientId,family,userId,input.invitationId(),Timestamp.from(input.expiresAt()));
            insertScopes("autorizacao_escopo","autorizacao_id",grant,input.scopes());
            if (jdbc.update("UPDATE convite_familiar SET estado='CONFIRMADO',confirmado_em=clock_timestamp(),version=version+1,updated_at=clock_timestamp() WHERE id=? AND estado='ACEITO'",
                input.invitationId())!=1) throw conflict("INVITATION_USED","O convite já foi confirmado.");
        } catch (DataIntegrityViolationException ex) { throw conflict("ACTIVE_GRANT_EXISTS","Já existe autorização ativa para esta conta."); }
        return grant(grant);
    }

    List<GrantView> listGrants(UUID userId, UUID patientId) {
        requirePatientOwner(userId,patientId);
        return jdbc.queryForList("SELECT id FROM autorizacao_paciente WHERE paciente_id=? ORDER BY created_at DESC,id DESC",UUID.class,patientId)
            .stream().map(this::grant).toList();
    }

    List<GrantView> listEffectiveForFamily(UUID userId) {
        return jdbc.queryForList("SELECT id FROM autorizacao_paciente WHERE familiar_id=? AND revogada_em IS NULL AND expira_em>clock_timestamp() ORDER BY created_at DESC,id DESC",UUID.class,userId)
            .stream().map(this::grant).toList();
    }

    @Transactional
    void revokeGrant(UUID userId, UUID patientId, UUID grantId, long expectedVersion) {
        requirePatientOwner(userId,patientId);
        Map<String,Object> row;
        try { row=jdbc.queryForMap("SELECT * FROM autorizacao_paciente WHERE id=? AND paciente_id=? FOR UPDATE",grantId,patientId); }
        catch (EmptyResultDataAccessException ex) { throw notFound(); }
        if (number(row,"version")!=expectedVersion) throw precondition();
        if (row.get("revogada_em")==null)
            jdbc.update("UPDATE autorizacao_paciente SET revogada_em=clock_timestamp(),version=version+1,updated_at=clock_timestamp() WHERE id=?",grantId);
    }

    @Transactional
    FamilyInvitationView revokeInvitation(UUID userId, UUID invitationId, long expectedVersion) {
        Map<String,Object> row=invitationRow(invitationId,true);
        requirePatientOwner(userId,(UUID)row.get("paciente_id"));
        if (number(row,"version")!=expectedVersion) throw precondition();
        if ("REVOGADO".equals(row.get("estado"))) return invitation(invitationId);
        jdbc.update("UPDATE autorizacao_paciente SET revogada_em=coalesce(revogada_em,clock_timestamp()),version=version+1,updated_at=clock_timestamp() WHERE convite_id=?",invitationId);
        jdbc.update("UPDATE convite_familiar SET estado='REVOGADO',revogado_em=clock_timestamp(),version=version+1,updated_at=clock_timestamp() WHERE id=?",invitationId);
        return invitation(invitationId);
    }

    private PatientView patientByOwner(UUID userId, boolean lock) {
        Map<String,Object> row=patientRowByOwner(userId,lock);
        return new PatientView((UUID)row.get("id"),(String)row.get("identidade_estado"),number(row,"version"));
    }
    private PatientView patient(UUID id) {
        Map<String,Object> row=patientRow(id,false);
        return new PatientView(id,(String)row.get("identidade_estado"),number(row,"version"));
    }
    private Map<String,Object> patientRowByOwner(UUID userId, boolean lock) {
        try { return jdbc.queryForMap("SELECT * FROM paciente WHERE usuario_id=?"+(lock?" FOR UPDATE":""),userId); }
        catch (EmptyResultDataAccessException ex) { throw notFound(); }
    }
    private Map<String,Object> patientRow(UUID id, boolean lock) {
        try { return jdbc.queryForMap("SELECT * FROM paciente WHERE id=?"+(lock?" FOR UPDATE":""),id); }
        catch (EmptyResultDataAccessException ex) { throw notFound(); }
    }
    private void requirePatientOwner(UUID userId, UUID patientId) {
        Map<String,Object> row=patientRow(patientId,false); if (!userId.equals(row.get("usuario_id"))) throw notFound();
    }
    private void requireActiveUser(UUID userId) {
        try { jdbc.queryForObject("SELECT id FROM usuario WHERE id=? AND estado='ATIVO' FOR UPDATE",UUID.class,userId); }
        catch (EmptyResultDataAccessException ex) { throw AuthService.unauthorized(); }
        if (!protector.configured()) throw unavailable();
    }
    private Map<String,Object> invitationRow(UUID id, boolean lock) {
        try { return jdbc.queryForMap("SELECT * FROM convite_familiar WHERE id=?"+(lock?" FOR UPDATE":""),id); }
        catch (EmptyResultDataAccessException ex) { throw notFound(); }
    }
    private boolean mayReadInvitation(UUID userId, Map<String,Object> row) {
        if (jdbc.queryForObject("SELECT count(*) FROM paciente WHERE id=? AND usuario_id=?",Long.class,row.get("paciente_id"),userId)>0) return true;
        byte[] account=jdbc.queryForObject("SELECT email_busca FROM usuario WHERE id=?",byte[].class,userId);
        return java.security.MessageDigest.isEqual(account,(byte[])row.get("destinatario_email_busca"));
    }
    private FamilyInvitationView invitation(UUID id) {
        Map<String,Object> row=invitationRow(id,false); String state=(String)row.get("estado");
        if ((state.equals("PENDENTE")||state.equals("ACEITO")) && !instant(row.get("expira_em")).isAfter(Instant.now())) state="EXPIRADO";
        return new FamilyInvitationView(id,(UUID)row.get("paciente_id"),state,scopes("convite_familiar_escopo","convite_id",id),instant(row.get("expira_em")),number(row,"version"));
    }
    private GrantView grant(UUID id) {
        Map<String,Object> row=jdbc.queryForMap("SELECT * FROM autorizacao_paciente WHERE id=?",id);
        return new GrantView(id,(UUID)row.get("paciente_id"),(UUID)row.get("familiar_id"),scopes("autorizacao_escopo","autorizacao_id",id),instant(row.get("expira_em")),row.get("revogada_em")==null?null:instant(row.get("revogada_em")),number(row,"version"),(UUID)row.get("convite_id"));
    }
    private Set<FamilyScope> scopes(String table, String column, UUID id) {
        return new LinkedHashSet<>(jdbc.queryForList("SELECT escopo FROM "+table+" WHERE "+column+"=? ORDER BY escopo",String.class,id).stream().map(FamilyScope::valueOf).toList());
    }
    private void insertScopes(String table, String column, UUID id, Set<FamilyScope> scopes) {
        for (FamilyScope scope:scopes) jdbc.update("INSERT INTO "+table+"("+column+",escopo) VALUES (?,?)",id,scope.name());
    }
    private long number(Map<String,Object> row,String key) { return ((Number)row.get(key)).longValue(); }
    private Instant instant(Object value) { return ((Timestamp)value).toInstant(); }
    private RepresentationException notFound() { return new RepresentationException(HttpStatus.NOT_FOUND,"NOT_FOUND","Recurso não encontrado.",false); }
    private RepresentationException precondition() { return new RepresentationException(HttpStatus.PRECONDITION_FAILED,"VERSION_MISMATCH","A versão do recurso mudou.",false); }
    private RepresentationException conflict(String code,String message) { return new RepresentationException(HttpStatus.CONFLICT,code,message,false); }
    private RepresentationException unavailable() { return new RepresentationException(HttpStatus.SERVICE_UNAVAILABLE,"INTEGRATION_UNAVAILABLE","O canal privado está indisponível. Nenhum convite foi criado.",true); }

    static final class RepresentationException extends ResponseStatusException {
        private final ApiError body;
        RepresentationException(HttpStatus status,String code,String message,boolean retryable) { super(status,message); body=new ApiError(code,message,UUID.randomUUID(),retryable); }
        ApiError body() { return body; }
    }
}
