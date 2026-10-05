package br.com.exameperto.identity;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
class DelivererService {
    private static final long MAX = 10L * 1024 * 1024;
    private final JdbcTemplate jdbc;
    private final PrivateObjectStore store;
    private final MfaService mfa;
    private final boolean fileSafetyInspectionEnabled;
    DelivererService(JdbcTemplate jdbc, PrivateObjectStore store, MfaService mfa,
        @Value("${review.file-safety-inspection-enabled:true}") boolean fileSafetyInspectionEnabled) {
        this.jdbc=jdbc; this.store=store; this.mfa=mfa; this.fileSafetyInspectionEnabled=fileSafetyInspectionEnabled;
    }

    @Transactional
    DelivererView createOrUpdate(UUID userId, DelivererInput input, Long expectedVersion) {
        active(userId);
        try {
            Map<String,Object> current;
            try { current = jdbc.queryForMap("SELECT id,version FROM entregador WHERE usuario_id=? FOR UPDATE", userId); }
            catch (EmptyResultDataAccessException ex) {
                UUID id = UUID.randomUUID();
                jdbc.update("INSERT INTO entregador(id,usuario_id,nascimento,estado) VALUES (?,?,?,'RASCUNHO')", id,userId,input.birthDate());
                return deliverer(id);
            }
            long version=((Number)current.get("version")).longValue();
            if (expectedVersion == null || expectedVersion != version) throw error(HttpStatus.PRECONDITION_FAILED,"VERSION_MISMATCH","O cadastro mudou; recarregue antes de atualizar.");
            jdbc.update("UPDATE entregador SET nascimento=?,estado='RASCUNHO',version=version+1,updated_at=clock_timestamp() WHERE id=? AND version=?", input.birthDate(),current.get("id"),version);
            return deliverer((UUID)current.get("id"));
        } catch (DataIntegrityViolationException ex) { throw error(HttpStatus.CONFLICT,"DELIVERER_CONFLICT","Não foi possível salvar o cadastro."); }
    }

    DelivererView own(UUID userId) {
        try { return deliverer(jdbc.queryForObject("SELECT id FROM entregador WHERE usuario_id=?", UUID.class,userId)); }
        catch (EmptyResultDataAccessException ex) { throw error(HttpStatus.NOT_FOUND,"NOT_FOUND","Cadastro não encontrado."); }
    }

    @Transactional
    DocumentView upload(UUID userId, String category, MultipartFile file) {
        DelivererView deliverer = own(userId);
        validate(category,file);
        UUID id=UUID.randomUUID(); String key=store.put(id,file);
        try {
            byte[] bytes=file.getBytes();
            jdbc.update("INSERT INTO documento(id,proprietario_id,categoria,objeto_chave,sha256,mime,tamanho,estado) VALUES (?,?,?,?,?,?,?,'QUARENTENA')",
                id,userId,category,key,MessageDigest.getInstance("SHA-256").digest(bytes),realMime(bytes),bytes.length);
            jdbc.update("INSERT INTO entregador_documento(entregador_id,documento_id,finalidade) VALUES ((SELECT id FROM entregador WHERE usuario_id=?),?,?)",userId,id,category);
            jdbc.update("UPDATE entregador SET estado='EM_ANALISE',version=version+1,updated_at=clock_timestamp() WHERE usuario_id=?",userId);
            jdbc.update("INSERT INTO revisao_entregador(id,entregador_id,documento_id,estado) VALUES (?,?,?,'PENDENTE')",UUID.randomUUID(),deliverer.id(),id);
            return document(id);
        } catch (Exception ex) { store.remove(key); if (ex instanceof DelivererException de) throw de; throw error(HttpStatus.SERVICE_UNAVAILABLE,"STORAGE_UNAVAILABLE","Não foi possível persistir o arquivo privado."); }
    }

    List<DocumentView> documents(UUID userId) {
        own(userId);
        return jdbc.query("SELECT d.id,d.categoria,d.mime,d.tamanho,d.estado,d.version FROM documento d WHERE d.proprietario_id=? ORDER BY d.created_at DESC,d.id DESC",
            (rs,n)->new DocumentView(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getLong(4),rs.getString(5),rs.getLong(6)),userId);
    }

    Download authorizedDownload(UUID userId, UUID documentId) {
        Map<String,Object> row;
        try { row=jdbc.queryForMap("SELECT id,proprietario_id,objeto_chave,mime,tamanho,estado FROM documento WHERE id=?",documentId); }
        catch (EmptyResultDataAccessException ex) { throw error(HttpStatus.NOT_FOUND,"NOT_FOUND","Documento não encontrado."); }
        if (!userId.equals(row.get("proprietario_id")) || !"INSPECAO_APROVADA".equals(row.get("estado")))
            throw error(HttpStatus.NOT_FOUND,"NOT_FOUND","Documento não encontrado.");
        return new Download((String)row.get("objeto_chave"),(String)row.get("mime"),((Number)row.get("tamanho")).longValue());
    }

    List<ReviewView> queue(UUID userId) { requireAnalyst(userId); return jdbc.query("SELECT id,entregador_id,documento_id,analista_id,estado,decisao,motivo_codigo FROM revisao_entregador WHERE estado IN ('PENDENTE','ATRIBUIDA') ORDER BY criado_em,id", (rs,n)->new ReviewView(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getObject(3,UUID.class),rs.getObject(4,UUID.class),rs.getString(5),rs.getString(6),rs.getString(7))); }

    @Transactional
    ReviewView assign(AuthService.SessionPrincipal principal, UUID reviewId) {
        UUID analystId=principal.userId(); requireAnalyst(analystId); mfa.requireVerified(principal);
        Map<String,Object> row=reviewForUpdate(reviewId);
        if (!"PENDENTE".equals(row.get("estado"))) throw error(HttpStatus.CONFLICT,"REVIEW_CONFLICT","A revisão já foi atribuída.");
        UUID owner=jdbc.queryForObject("SELECT e.usuario_id FROM entregador e WHERE e.id=?",UUID.class,row.get("entregador_id"));
        if (analystId.equals(owner)) throw error(HttpStatus.FORBIDDEN,"FORBIDDEN","O analista não pode revisar o próprio cadastro.");
        jdbc.update("UPDATE revisao_entregador SET analista_id=?,estado='ATRIBUIDA',atribuido_em=clock_timestamp() WHERE id=?",analystId,reviewId);
        return review(reviewId);
    }
    @Transactional
    ReviewView inspect(AuthService.SessionPrincipal principal, UUID reviewId) {
        UUID analystId=principal.userId(); requireAnalyst(analystId); mfa.requireVerified(principal);
        Map<String,Object> row=assignedReview(reviewId,analystId);
        if (!fileSafetyInspectionEnabled) throw error(HttpStatus.SERVICE_UNAVAILABLE,"INTEGRATION_UNAVAILABLE","A inspeção de segurança do arquivo não está habilitada.");
        UUID documentId=(UUID)row.get("documento_id");
        Map<String,Object> document=jdbc.queryForMap("SELECT objeto_chave,mime,estado FROM documento WHERE id=? FOR UPDATE",documentId);
        if (!"QUARENTENA".equals(document.get("estado")) && !"INSPECAO_PENDENTE".equals(document.get("estado")))
            throw error(HttpStatus.CONFLICT,"REVIEW_CONFLICT","O arquivo não está aguardando inspeção.");
        boolean safe;
        try(InputStream input=store.open((String)document.get("objeto_chave"))){safe=safeStructure(input.readAllBytes(),(String)document.get("mime"));}
        catch(Exception ex){throw error(HttpStatus.SERVICE_UNAVAILABLE,"STORAGE_UNAVAILABLE","Não foi possível inspecionar o arquivo privado.");}
        jdbc.update("UPDATE documento SET estado=?,inspeccionado_em=clock_timestamp(),updated_at=clock_timestamp(),version=version+1 WHERE id=?",safe?"INSPECAO_APROVADA":"REJEITADO",documentId);
        if(!safe) jdbc.update("UPDATE revisao_entregador SET estado='BLOQUEADA',motivo_codigo='ARQUIVO_ESTRUTURALMENTE_INSEGURO' WHERE id=?",reviewId);
        return review(reviewId);
    }
    @Transactional
    ReviewView decide(AuthService.SessionPrincipal principal, UUID reviewId, String decision, String reason) {
        UUID analystId=principal.userId(); requireAnalyst(analystId); mfa.requireVerified(principal); assignedReview(reviewId,analystId);
        throw error(HttpStatus.SERVICE_UNAVAILABLE,"POLICY_UNDEFINED","A aprovação profissional permanece bloqueada até critérios e responsáveis reais serem habilitados.");
    }

    InputStream open(String key) { return store.open(key); }

    private void validate(String category, MultipartFile file) {
        if (!List.of("IDENTIDADE","HABILITACAO","VEICULO","FOTO_OPERACIONAL").contains(category)) throw error(HttpStatus.BAD_REQUEST,"INVALID_INPUT","Categoria de documento inválida.");
        if (file==null || file.isEmpty() || file.getSize()>MAX) throw error(HttpStatus.PAYLOAD_TOO_LARGE,"FILE_TOO_LARGE","Arquivo vazio ou acima do limite de 10 MiB.");
        try { realMime(file.getBytes()); } catch (Exception ex) { throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_FILE","Tipo real de arquivo não permitido."); }
    }
    private String realMime(byte[] b) {
        if (b.length>=4 && b[0]=='%' && b[1]=='P' && b[2]=='D' && b[3]=='F') return "application/pdf";
        if (b.length>=8 && (b[0]&255)==0x89 && b[1]=='P' && b[2]=='N' && b[3]=='G') return "image/png";
        if (b.length>=3 && (b[0]&255)==0xff && (b[1]&255)==0xd8 && (b[2]&255)==0xff) return "image/jpeg";
        throw new IllegalArgumentException();
    }
    private void active(UUID id) { try { if (!"ATIVO".equals(jdbc.queryForObject("SELECT estado FROM usuario WHERE id=?",String.class,id))) throw AuthService.unauthorized(); } catch (EmptyResultDataAccessException ex) { throw AuthService.unauthorized(); } }
    private void requireAnalyst(UUID id) { active(id); if (jdbc.queryForObject("SELECT count(*) FROM papel_global WHERE usuario_id=? AND papel='ANALISTA_OPERACIONAL' AND revogado_em IS NULL",Long.class,id)==0) throw error(HttpStatus.FORBIDDEN,"FORBIDDEN","Analista atribuído não encontrado."); }
    private Map<String,Object> reviewForUpdate(UUID id){try{return jdbc.queryForMap("SELECT id,entregador_id,documento_id,analista_id,estado FROM revisao_entregador WHERE id=? FOR UPDATE",id);}catch(EmptyResultDataAccessException ex){throw error(HttpStatus.NOT_FOUND,"NOT_FOUND","Revisão não encontrada.");}}
    private Map<String,Object> assignedReview(UUID id,UUID analyst){Map<String,Object> row=reviewForUpdate(id);if(!analyst.equals(row.get("analista_id")) || !"ATRIBUIDA".equals(row.get("estado")))throw error(HttpStatus.NOT_FOUND,"NOT_FOUND","Revisão atribuída não encontrada.");return row;}
    private ReviewView review(UUID id){return jdbc.queryForObject("SELECT id,entregador_id,documento_id,analista_id,estado,decisao,motivo_codigo FROM revisao_entregador WHERE id=?",(rs,n)->new ReviewView(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getObject(3,UUID.class),rs.getObject(4,UUID.class),rs.getString(5),rs.getString(6),rs.getString(7)),id);}
    private boolean safeStructure(byte[] bytes,String mime){
        if(bytes.length==0||bytes.length>MAX)return false;
        if("application/pdf".equals(mime)){String value=new String(bytes,StandardCharsets.ISO_8859_1);return value.startsWith("%PDF-")&&value.stripTrailing().endsWith("%%EOF")&&!List.of("/JavaScript","/JS","/Launch","/EmbeddedFile","/OpenAction","/RichMedia").stream().anyMatch(value::contains);}
        if("image/jpeg".equals(mime))return bytes.length>=4&&(bytes[bytes.length-2]&255)==0xff&&(bytes[bytes.length-1]&255)==0xd9;
        if("image/png".equals(mime)){byte[] end={(byte)0x49,(byte)0x45,(byte)0x4e,(byte)0x44,(byte)0xae,(byte)0x42,(byte)0x60,(byte)0x82};if(bytes.length<end.length)return false;for(int i=0;i<end.length;i++)if(bytes[bytes.length-end.length+i]!=end[i])return false;return true;}
        return false;
    }
    private DelivererView deliverer(UUID id) { return jdbc.queryForObject("SELECT id,estado,version FROM entregador WHERE id=?",(rs,n)->new DelivererView(rs.getObject(1,UUID.class),rs.getString(2),rs.getLong(3)),id); }
    private DocumentView document(UUID id) { return jdbc.queryForObject("SELECT id,categoria,mime,tamanho,estado,version FROM documento WHERE id=?",(rs,n)->new DocumentView(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getLong(4),rs.getString(5),rs.getLong(6)),id); }
    record Download(String key,String mime,long size) {}
    static DelivererException error(HttpStatus s,String c,String m) { return new DelivererException(s,c,m); }
    static final class DelivererException extends ResponseStatusException { private final ApiError body; DelivererException(HttpStatus s,String c,String m){super(s,m);body=new ApiError(c,m,UUID.randomUUID(),s.is5xxServerError());} ApiError body(){return body;} }
}
