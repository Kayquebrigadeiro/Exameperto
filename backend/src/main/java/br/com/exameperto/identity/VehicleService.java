package br.com.exameperto.identity;

import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
class VehicleService {
    private final JdbcTemplate jdbc;
    private final DelivererService evidence;
    private final MfaService mfa;

    VehicleService(JdbcTemplate jdbc, DelivererService evidence, MfaService mfa) {
        this.jdbc=jdbc; this.evidence=evidence; this.mfa=mfa;
    }

    @Transactional
    VehicleLinkView create(UUID userId, VehicleInput input) {
        UUID deliverer=deliverer(userId); validate(input);
        String plate=plate(input.plate());
        UUID vehicle;
        try {
            Map<String,Object> existing=jdbc.queryForMap("SELECT id,marca,modelo,cor,ano_fabricacao,ano_modelo FROM veiculo WHERE placa=? FOR UPDATE",plate);
            if (!sameVehicle(existing,input)) throw error(HttpStatus.CONFLICT,"VEHICLE_CONFLICT","A placa já está associada a dados diferentes; a divergência exige revisão operacional.");
            vehicle=(UUID)existing.get("id");
        } catch (EmptyResultDataAccessException ex) {
            vehicle=UUID.randomUUID();
            try { jdbc.update("INSERT INTO veiculo(id,placa,marca,modelo,cor,ano_fabricacao,ano_modelo,estado) VALUES (?,?,?,?,?,?,?,'RASCUNHO')",
                vehicle,plate,clean(input.make()),clean(input.model()),clean(input.color()),input.manufacturingYear(),input.modelYear()); }
            catch (DataIntegrityViolationException race) { throw error(HttpStatus.CONFLICT,"VEHICLE_CONFLICT","A placa foi cadastrada concorrentemente; recarregue antes de continuar."); }
        }
        UUID link=UUID.randomUUID();
        jdbc.update("INSERT INTO vinculo_veiculo(id,entregador_id,veiculo_id,tipo,estado,valido_ate) VALUES (?,?,?,?, 'RASCUNHO',?)",
            link,deliverer,vehicle,input.linkType().name(),input.validUntil());
        return link(userId,link);
    }

    @Transactional
    VehicleLinkView update(UUID userId, UUID linkId, long expectedVersion, VehicleInput input) {
        validate(input); Map<String,Object> link=ownForUpdate(userId,linkId); requireVersion(link,expectedVersion);
        UUID vehicle=(UUID)link.get("veiculo_id"); String normalized=plate(input.plate());
        long otherLinks=jdbc.queryForObject("SELECT count(*) FROM vinculo_veiculo WHERE veiculo_id=? AND id<>?",Long.class,vehicle,linkId);
        if (otherLinks>0) throw error(HttpStatus.CONFLICT,"VEHICLE_SHARED","Dados de um veículo com outro vínculo não podem ser alterados por esta conta.");
        try {
            jdbc.update("UPDATE veiculo SET placa=?,marca=?,modelo=?,cor=?,ano_fabricacao=?,ano_modelo=?,estado='RASCUNHO',version=version+1,updated_at=clock_timestamp() WHERE id=?",
                normalized,clean(input.make()),clean(input.model()),clean(input.color()),input.manufacturingYear(),input.modelYear(),vehicle);
            jdbc.update("UPDATE vinculo_veiculo SET tipo=?,valido_ate=?,estado=?,version=version+1,updated_at=clock_timestamp() WHERE id=?",
                input.linkType().name(),input.validUntil(),hasDocuments(linkId)?"EM_ANALISE":"RASCUNHO",linkId);
            replaceReview(linkId,"DADOS_SUBSTITUIDOS");
        } catch (DataIntegrityViolationException ex) { throw error(HttpStatus.CONFLICT,"VEHICLE_CONFLICT","Não foi possível atualizar os dados do veículo."); }
        return link(userId,linkId);
    }

    List<VehicleLinkView> own(UUID userId) {
        deliverer(userId);
        List<UUID> ids=jdbc.query("SELECT v.id FROM vinculo_veiculo v JOIN entregador e ON e.id=v.entregador_id WHERE e.usuario_id=? ORDER BY v.created_at DESC,v.id",
            (rs,n)->rs.getObject(1,UUID.class),userId);
        return ids.stream().map(id->link(userId,id)).toList();
    }

    VehicleLinkView link(UUID userId, UUID linkId) {
        try {
            return jdbc.queryForObject("SELECT vv.id,vv.veiculo_id,v.placa,v.marca,v.modelo,v.cor,v.ano_fabricacao,v.ano_modelo,vv.tipo,vv.valido_ate,vv.estado,vv.version FROM vinculo_veiculo vv JOIN veiculo v ON v.id=vv.veiculo_id JOIN entregador e ON e.id=vv.entregador_id WHERE vv.id=? AND e.usuario_id=?",
                (rs,n)->new VehicleLinkView(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getInt(7),rs.getInt(8),rs.getString(9),rs.getObject(10,LocalDate.class),rs.getString(11),rs.getLong(12),complete(linkId,rs.getString(9)),documents(linkId)),linkId,userId);
        } catch (EmptyResultDataAccessException ex) { throw error(HttpStatus.NOT_FOUND,"NOT_FOUND","Vínculo de veículo não encontrado."); }
    }

    @Transactional
    VehicleLinkView upload(UUID userId, UUID linkId, long expectedVersion, ReuseDocumentInput.Purpose purpose, MultipartFile file) {
        Map<String,Object> link=ownForUpdate(userId,linkId); requireVersion(link,expectedVersion); validatePurpose((String)link.get("tipo"),purpose);
        DelivererService.StoredEvidence stored=evidence.storeEvidence(userId,"VEICULO",file);
        try { attach(linkId,stored.id(),purpose); }
        catch (RuntimeException ex) { evidence.discardEvidence(stored); throw ex; }
        return link(userId,linkId);
    }

    @Transactional
    VehicleLinkView reuse(UUID userId, UUID linkId, ReuseDocumentInput input) {
        Map<String,Object> link=ownForUpdate(userId,linkId); requireVersion(link,input.linkVersion()); validatePurpose((String)link.get("tipo"),input.purpose());
        long owned=jdbc.queryForObject("SELECT count(*) FROM documento WHERE id=? AND proprietario_id=? AND categoria='VEICULO' AND estado NOT IN ('REJEITADO','EXPURGADO')",Long.class,input.documentId(),userId);
        if (owned!=1) throw error(HttpStatus.NOT_FOUND,"NOT_FOUND","Documento reutilizável não encontrado.");
        attach(linkId,input.documentId(),input.purpose());
        return link(userId,linkId);
    }

    List<VehicleReviewView> queue(UUID analystId) {
        evidence.requireAnalyst(analystId);
        return jdbc.query("SELECT id FROM revisao_vinculo_veiculo WHERE estado IN ('PENDENTE','ATRIBUIDA') ORDER BY criado_em,id",
            (rs,n)->review(rs.getObject(1,UUID.class)));
    }

    @Transactional
    VehicleReviewView assign(AuthService.SessionPrincipal principal, UUID reviewId) {
        UUID analyst=principal.userId(); evidence.requireAnalyst(analyst); mfa.requireVerified(principal);
        Map<String,Object> row=reviewForUpdate(reviewId);
        if (!"PENDENTE".equals(row.get("estado"))) throw error(HttpStatus.CONFLICT,"REVIEW_CONFLICT","A revisão já foi atribuída ou invalidada.");
        UUID owner=jdbc.queryForObject("SELECT e.usuario_id FROM vinculo_veiculo vv JOIN entregador e ON e.id=vv.entregador_id WHERE vv.id=?",UUID.class,row.get("vinculo_id"));
        if (analyst.equals(owner)) throw error(HttpStatus.FORBIDDEN,"FORBIDDEN","O analista não pode revisar o próprio vínculo.");
        assertCurrent(row);
        jdbc.update("UPDATE revisao_vinculo_veiculo SET analista_id=?,estado='ATRIBUIDA',atribuido_em=clock_timestamp() WHERE id=?",analyst,reviewId);
        return review(reviewId);
    }

    @Transactional
    VehicleReviewView inspect(AuthService.SessionPrincipal principal, UUID reviewId, UUID documentId) {
        Map<String,Object> row=assigned(principal,reviewId); assertCurrent(row);
        long member=jdbc.queryForObject("SELECT count(*) FROM revisao_vinculo_documento WHERE revisao_id=? AND documento_id=?",Long.class,reviewId,documentId);
        if (member!=1) throw error(HttpStatus.NOT_FOUND,"NOT_FOUND","Documento da revisão não encontrado.");
        boolean safe=evidence.inspectEvidence(documentId);
        if (!safe) jdbc.update("UPDATE revisao_vinculo_veiculo SET estado='BLOQUEADA',motivo_codigo='ARQUIVO_ESTRUTURALMENTE_INSEGURO' WHERE id=?",reviewId);
        return review(reviewId);
    }

    DelivererService.Download download(AuthService.SessionPrincipal principal, UUID reviewId, UUID documentId) {
        Map<String,Object> row=assigned(principal,reviewId); assertCurrent(row);
        long member=jdbc.queryForObject("SELECT count(*) FROM revisao_vinculo_documento WHERE revisao_id=? AND documento_id=?",Long.class,reviewId,documentId);
        if (member!=1) throw error(HttpStatus.NOT_FOUND,"NOT_FOUND","Documento da revisão não encontrado.");
        return evidence.inspectedDownload(documentId);
    }

    @Transactional
    VehicleReviewView decide(AuthService.SessionPrincipal principal, UUID reviewId, String decision, String reason) {
        Map<String,Object> row=assigned(principal,reviewId); assertCurrent(row);
        UUID linkId=(UUID)row.get("vinculo_id"); String type=jdbc.queryForObject("SELECT tipo FROM vinculo_veiculo WHERE id=?",String.class,linkId);
        if (!complete(linkId,type)) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"EVIDENCE_INCOMPLETE","CRLV, foto e, quando aplicável, evidência de uso autorizado são obrigatórios.");
        long pending=jdbc.queryForObject("SELECT count(*) FROM revisao_vinculo_documento rvd JOIN documento d ON d.id=rvd.documento_id WHERE rvd.revisao_id=? AND d.estado<>'INSPECAO_APROVADA'",Long.class,reviewId);
        if (pending>0) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INSPECTION_REQUIRED","Todos os arquivos precisam concluir a inspeção estrutural.");
        throw error(HttpStatus.UNPROCESSABLE_ENTITY,"POLICY_UNDEFINED","A aprovação profissional permanece bloqueada até critérios, responsáveis e integrações reais serem habilitados.");
    }

    private void attach(UUID linkId, UUID documentId, ReuseDocumentInput.Purpose purpose) {
        jdbc.update("UPDATE vinculo_documento SET substituido_em=clock_timestamp() WHERE vinculo_id=? AND finalidade=? AND substituido_em IS NULL",linkId,purpose.name());
        try { jdbc.update("INSERT INTO vinculo_documento(vinculo_id,documento_id,finalidade) VALUES (?,?,?)",linkId,documentId,purpose.name()); }
        catch (DataIntegrityViolationException ex) { throw error(HttpStatus.CONFLICT,"DOCUMENT_CONFLICT","O documento já está registrado neste vínculo."); }
        jdbc.update("UPDATE vinculo_veiculo SET estado='EM_ANALISE',version=version+1,updated_at=clock_timestamp() WHERE id=?",linkId);
        replaceReview(linkId,"EVIDENCIA_SUBSTITUIDA");
    }

    private void replaceReview(UUID linkId, String reason) {
        jdbc.update("UPDATE revisao_vinculo_veiculo SET estado='BLOQUEADA',motivo_codigo=? WHERE vinculo_id=? AND estado IN ('PENDENTE','ATRIBUIDA')",reason,linkId);
        if (!hasDocuments(linkId)) return;
        long version=jdbc.queryForObject("SELECT version FROM vinculo_veiculo WHERE id=?",Long.class,linkId); UUID review=UUID.randomUUID();
        jdbc.update("INSERT INTO revisao_vinculo_veiculo(id,vinculo_id,versao_vinculo,estado) VALUES (?,?,?,'PENDENTE')",review,linkId,version);
        jdbc.update("INSERT INTO revisao_vinculo_documento(revisao_id,documento_id,finalidade) SELECT ?,documento_id,finalidade FROM vinculo_documento WHERE vinculo_id=? AND substituido_em IS NULL",review,linkId);
    }

    private Map<String,Object> ownForUpdate(UUID userId, UUID linkId) {
        evidence.active(userId);
        try { return jdbc.queryForMap("SELECT vv.id,vv.veiculo_id,vv.tipo,vv.version FROM vinculo_veiculo vv JOIN entregador e ON e.id=vv.entregador_id WHERE vv.id=? AND e.usuario_id=? FOR UPDATE OF vv",linkId,userId); }
        catch (EmptyResultDataAccessException ex) { throw error(HttpStatus.NOT_FOUND,"NOT_FOUND","Vínculo de veículo não encontrado."); }
    }
    private UUID deliverer(UUID userId) { evidence.active(userId); try{return jdbc.queryForObject("SELECT id FROM entregador WHERE usuario_id=?",UUID.class,userId);}catch(EmptyResultDataAccessException ex){throw error(HttpStatus.CONFLICT,"DELIVERER_REQUIRED","Crie o cadastro de entregador antes do vínculo de veículo.");} }
    private void requireVersion(Map<String,Object> row,long expected){if(((Number)row.get("version")).longValue()!=expected)throw error(HttpStatus.PRECONDITION_FAILED,"VERSION_MISMATCH","O vínculo mudou; recarregue antes de atualizar.");}
    private Map<String,Object> reviewForUpdate(UUID id){try{return jdbc.queryForMap("SELECT id,vinculo_id,versao_vinculo,analista_id,estado FROM revisao_vinculo_veiculo WHERE id=? FOR UPDATE",id);}catch(EmptyResultDataAccessException ex){throw error(HttpStatus.NOT_FOUND,"NOT_FOUND","Revisão de vínculo não encontrada.");}}
    private Map<String,Object> assigned(AuthService.SessionPrincipal principal,UUID id){UUID analyst=principal.userId();evidence.requireAnalyst(analyst);mfa.requireVerified(principal);Map<String,Object> row=reviewForUpdate(id);if(!analyst.equals(row.get("analista_id"))||!"ATRIBUIDA".equals(row.get("estado")))throw error(HttpStatus.NOT_FOUND,"NOT_FOUND","Revisão atribuída não encontrada.");return row;}
    private void assertCurrent(Map<String,Object> row){long current=jdbc.queryForObject("SELECT version FROM vinculo_veiculo WHERE id=?",Long.class,row.get("vinculo_id"));if(current!=((Number)row.get("versao_vinculo")).longValue())throw error(HttpStatus.CONFLICT,"STALE_REVIEW","A revisão ficou desatualizada após uma substituição.");}
    private boolean hasDocuments(UUID linkId){return jdbc.queryForObject("SELECT count(*) FROM vinculo_documento WHERE vinculo_id=? AND substituido_em IS NULL",Long.class,linkId)>0;}
    private boolean complete(UUID linkId,String type){long required=List.of("LOCACAO","AUTORIZACAO").contains(type)?3:2;return jdbc.queryForObject("SELECT count(DISTINCT finalidade) FROM vinculo_documento WHERE vinculo_id=? AND substituido_em IS NULL",Long.class,linkId)>=required;}
    private List<VehicleDocumentView> documents(UUID linkId){return jdbc.query("SELECT d.id,vd.finalidade,d.mime,d.tamanho,d.estado,vd.substituido_em IS NULL FROM vinculo_documento vd JOIN documento d ON d.id=vd.documento_id WHERE vd.vinculo_id=? ORDER BY vd.criado_em DESC,d.id",(rs,n)->new VehicleDocumentView(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getLong(4),rs.getString(5),rs.getBoolean(6)),linkId);}
    private VehicleReviewView review(UUID id){return jdbc.queryForObject("SELECT id,vinculo_id,versao_vinculo,analista_id,estado,decisao,motivo_codigo FROM revisao_vinculo_veiculo WHERE id=?",(rs,n)->new VehicleReviewView(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getLong(3),rs.getObject(4,UUID.class),rs.getString(5),rs.getString(6),rs.getString(7),reviewDocuments(id)),id);}
    private List<VehicleDocumentView> reviewDocuments(UUID id){return jdbc.query("SELECT d.id,rvd.finalidade,d.mime,d.tamanho,d.estado,true FROM revisao_vinculo_documento rvd JOIN documento d ON d.id=rvd.documento_id WHERE rvd.revisao_id=? ORDER BY rvd.finalidade,d.id",(rs,n)->new VehicleDocumentView(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getLong(4),rs.getString(5),rs.getBoolean(6)),id);}
    private void validate(VehicleInput input){int max=Year.now().getValue()+1;if(input.manufacturingYear()<1886||input.manufacturingYear()>max||input.modelYear()<input.manufacturingYear()||input.modelYear()>Math.min(max,input.manufacturingYear()+1))throw error(HttpStatus.BAD_REQUEST,"INVALID_INPUT","Confira os anos de fabricação e modelo.");if(input.validUntil()!=null&&input.validUntil().isBefore(LocalDate.now()))throw error(HttpStatus.BAD_REQUEST,"INVALID_INPUT","A validade do vínculo não pode estar vencida.");}
    private void validatePurpose(String type,ReuseDocumentInput.Purpose purpose){if(type.equals("PROPRIEDADE")&&purpose==ReuseDocumentInput.Purpose.USO_AUTORIZADO)throw error(HttpStatus.BAD_REQUEST,"INVALID_INPUT","Evidência de autorização não se aplica ao vínculo próprio.");}
    private boolean sameVehicle(Map<String,Object> row,VehicleInput input){return clean(input.make()).equals(row.get("marca"))&&clean(input.model()).equals(row.get("modelo"))&&clean(input.color()).equals(row.get("cor"))&&input.manufacturingYear()==((Number)row.get("ano_fabricacao")).intValue()&&input.modelYear()==((Number)row.get("ano_modelo")).intValue();}
    private String plate(String value){String p=value.replaceAll("[^A-Za-z0-9]","").toUpperCase(java.util.Locale.ROOT);if(p.length()!=7)throw error(HttpStatus.BAD_REQUEST,"INVALID_INPUT","A placa deve conter sete caracteres alfanuméricos.");return p;}
    private String clean(String value){return value.strip().replaceAll("\\s+"," ");}
    private static DelivererService.DelivererException error(HttpStatus status,String code,String message){return DelivererService.error(status,code,message);}
}
