package br.com.exameperto.identity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class OrderService {
    private final JdbcTemplate jdbc; private final DataProtector protector; private final ObjectMapper json; private final RouteProvider routes; private final DelivererService evidence;
    OrderService(JdbcTemplate jdbc, DataProtector protector, ObjectMapper json, RouteProvider routes, DelivererService evidence){this.jdbc=jdbc;this.protector=protector;this.json=json;this.routes=routes;this.evidence=evidence;}

    @Transactional
    OrderView create(UUID actor, OrderInput input, String idem) {
        requireAddress(input.origin()); requireAddress(input.destination()); authorizePedidos(actor,input.patientId()); authorizeRecipient(input.patientId(),input.recipientUserId());
        requireOwnedAuthorization(actor,input.pickupAuthorizationDocumentId());
        UUID existing=replay(actor,"CREATE_ORDER",idem,hash(input)); if(existing!=null)return order(existing);
        UUID id=UUID.randomUUID(); UUID auth=UUID.randomUUID();
        try {
            jdbc.update("INSERT INTO pedido(id,paciente_id,solicitante_id,origem_cifrada,destino_cifrada,origem_lat,origem_lon,destino_lat,destino_lon,destinatario_id,unidade_id,estado) VALUES (?,?,?,?,?,?,?,?,?,?,?,'EM_VERIFICACAO')",
                id,input.patientId(),actor,protect(input.origin()),protect(input.destination()),input.origin().latitude(),input.origin().longitude(),input.destination().latitude(),input.destination().longitude(),input.recipientUserId(),input.pickupUnitId());
            jdbc.update("INSERT INTO autorizacao_retirada(id,pedido_id,documento_id,estado,criada_por,valida_ate) VALUES (?,?,?,'PENDENTE',?,clock_timestamp()+interval '24 hours')",auth,id,input.pickupAuthorizationDocumentId(),actor);
            if(idem!=null)jdbc.update("INSERT INTO pedido_idempotencia(ator_id,operacao,chave,request_hash,pedido_id) VALUES (?,?,?,?,?)",actor,"CREATE_ORDER",idem,hash(input),id);
        } catch (RuntimeException ex) { throw error(HttpStatus.CONFLICT,"ORDER_CONFLICT","Não foi possível registrar o pedido."); }
        return order(id);
    }
    DocumentView uploadAuthorizationDocument(UUID actor, org.springframework.web.multipart.MultipartFile file){return evidence.storeDocument(actor,"AUTORIZACAO_RETIRADA",file);}

    List<OrderView> list(UUID actor){return jdbc.query("SELECT p.id FROM pedido p LEFT JOIN autorizacao_paciente a ON a.paciente_id=p.paciente_id AND a.familiar_id=? AND a.revogada_em IS NULL AND a.expira_em>clock_timestamp() JOIN paciente patient ON patient.id=p.paciente_id WHERE patient.usuario_id=? OR (a.id IS NOT NULL AND EXISTS (SELECT 1 FROM autorizacao_escopo e WHERE e.autorizacao_id=a.id AND e.escopo='PEDIDOS')) GROUP BY p.id,p.created_at ORDER BY p.created_at DESC,p.id",(rs,n)->order(rs.getObject(1,UUID.class)),actor,actor);}
    OrderView get(UUID actor,UUID id){authorizeRead(actor,id);return order(id);}
    OrderAddressesView addresses(UUID actor,UUID id){authorizeRead(actor,id);Map<String,Object> r=row(id);return new OrderAddressesView(read((byte[])r.get("origem_cifrada")),read((byte[])r.get("destino_cifrada")));}

    @Transactional
    OrderView updateAddresses(UUID actor,UUID id,OrderAddressUpdateInput input){
        requireAddress(input.origin()); requireAddress(input.destination()); authorizeRead(actor,id); Map<String,Object> row=forUpdate(id); requireVersion(row,input.version());
        jdbc.update("UPDATE pedido SET origem_cifrada=?,destino_cifrada=?,origem_lat=?,origem_lon=?,destino_lat=?,destino_lon=?,estado='EM_VERIFICACAO',version=version+1,updated_at=clock_timestamp() WHERE id=? AND version=?",protect(input.origin()),protect(input.destination()),input.origin().latitude(),input.origin().longitude(),input.destination().latitude(),input.destination().longitude(),id,input.version());
        jdbc.update("UPDATE orcamento SET estado='SUBSTITUIDO',version=version+1 WHERE pedido_id=? AND estado IN ('PROPOSTO','ACEITO')",id); return order(id);
    }

    @Transactional
    OrderView attachAuthorization(UUID actor,UUID orderId,org.springframework.web.multipart.MultipartFile file){
        authorizeRead(actor,orderId); Map<String,Object> o=forUpdate(orderId); DelivererService.StoredEvidence stored=evidence.storeEvidence(actor,"AUTORIZACAO_RETIRADA",file);
        try {Map<String,Object> current=authorization(orderId); jdbc.update("UPDATE autorizacao_retirada SET documento_id=?,estado='PENDENTE',revogada_em=NULL WHERE id=?",stored.id(),current.get("id")); return order(orderId);} catch(RuntimeException ex){evidence.discardEvidence(stored);throw ex;}
    }

    @Transactional
    QuoteView quote(UUID actor,UUID orderId,long version,String idem,QuoteInput input){
        authorizeRead(actor,orderId); Map<String,Object> o=forUpdate(orderId);
        UUID old=replayQuote(actor,idem,hash(input)); if(old!=null)return quote(old);
        requireVersion(o,version); expireQuotes(orderId);
        Map<String,Object> auth=authorization(orderId); if(!"VERIFICADA".equals(auth.get("estado"))||auth.get("revogada_em")!=null||((java.sql.Timestamp)auth.get("valida_ate")).toInstant().isBefore(Instant.now()))throw error(HttpStatus.UNPROCESSABLE_ENTITY,"UNIT_NOT_CONFIRMED","A unidade ainda não confirmou o procedimento de retirada.");
        Address origin=read((byte[])o.get("origem_cifrada")), destination=read((byte[])o.get("destino_cifrada"));
        RouteProvider.RouteResult route=routes.route(origin,destination); validateRoute(route);
        Map<String,Object> tariff=activeTariff(); BigDecimal amount=calculate(route,tariff); UUID id=UUID.randomUUID(); Instant now=Instant.now();
        String snapshot=String.valueOf(tariff.get("parametros"));
        jdbc.update("UPDATE orcamento SET estado='SUBSTITUIDO',version=version+1 WHERE pedido_id=? AND estado='PROPOSTO'",orderId);
        jdbc.update("INSERT INTO orcamento(id,pedido_id,paciente_id,tarifa_id,rota_provedor,rota_referencia,distancia_m,duracao_s,rota_calculada_em,transito_incluido,politica_snapshot,frete,paciente_valor,subsidio_valor,moeda,expira_em,estado) VALUES (?,?,?,?,?,?,?,?,?,?,?::jsonb,?,?,0,'BRL',?,'PROPOSTO')",
            id,orderId,o.get("paciente_id"),tariff.get("id"),route.provider(),route.reference(),route.distanceMeters(),route.durationSeconds(),java.sql.Timestamp.from(route.calculatedAt()),route.trafficIncluded(),snapshot,amount,amount,java.sql.Timestamp.from(now.plusSeconds(validitySeconds(tariff))));
        jdbc.update("UPDATE pedido SET estado='AGUARDANDO_ACEITE',version=version+1,updated_at=clock_timestamp() WHERE id=? AND version=?",orderId,version);
        if(idem!=null)jdbc.update("INSERT INTO pedido_idempotencia(ator_id,operacao,chave,request_hash,orcamento_id) VALUES (?,?,?,?,?)",actor,"CREATE_QUOTE",idem,hash(input),id);
        return quote(id);
    }

    List<QuoteView> quotes(UUID actor,UUID orderId){authorizeRead(actor,orderId);expireQuotes(orderId);return jdbc.query("SELECT id FROM orcamento WHERE pedido_id=? ORDER BY created_at DESC,id",(rs,n)->quote(rs.getObject(1,UUID.class)),orderId);}
    QuoteView getQuote(UUID actor,UUID id){Map<String,Object> q=quoteRow(id);authorizeRead(actor,(UUID)q.get("pedido_id"));expireQuotes((UUID)q.get("pedido_id"));return quote(id);}

    private BigDecimal calculate(RouteProvider.RouteResult route,Map<String,Object> tariff){
        if(!"BASE_KM_MINUTO".equals(tariff.get("formula_codigo")))throw error(HttpStatus.UNPROCESSABLE_ENTITY,"POLICY_UNDEFINED","A fórmula tarifária não está definida.");
        try {
            Map<?,?> p=json.readValue(String.valueOf(tariff.get("parametros")),Map.class); BigDecimal base=decimal(p,"base"),km=decimal(p,"perKm"),minute=decimal(p,"perMinute"),minimum=decimal(p,"minimum");
            BigDecimal value=base.add(km.multiply(BigDecimal.valueOf(route.distanceMeters()).divide(BigDecimal.valueOf(1000),6,RoundingMode.HALF_UP))).add(minute.multiply(BigDecimal.valueOf(route.durationSeconds()).divide(BigDecimal.valueOf(60),6,RoundingMode.HALF_UP)));
            return value.max(minimum).setScale(2,RoundingMode.HALF_UP);
        } catch(Exception ex){throw error(HttpStatus.UNPROCESSABLE_ENTITY,"POLICY_UNDEFINED","Parâmetros tarifários inválidos.");}
    }
    private BigDecimal decimal(Map<?,?> p,String key){Object v=p.get(key);if(v==null)throw new IllegalArgumentException();return new BigDecimal(String.valueOf(v));}
    private long validitySeconds(Map<String,Object> tariff){try{Map<?,?> p=json.readValue(String.valueOf(tariff.get("parametros")),Map.class);Object value=p.containsKey("validitySeconds")?p.get("validitySeconds"):900;return Long.parseLong(String.valueOf(value));}catch(Exception e){return 900;}}
    private Map<String,Object> activeTariff(){try{return jdbc.queryForMap("SELECT * FROM tarifa WHERE estado='ATIVA' AND inicio<=clock_timestamp() AND (fim IS NULL OR fim>clock_timestamp()) ORDER BY numero DESC LIMIT 1");}catch(EmptyResultDataAccessException e){throw error(HttpStatus.UNPROCESSABLE_ENTITY,"POLICY_UNDEFINED","Não há política tarifária vigente.");}}
    private void validateRoute(RouteProvider.RouteResult r){if(r==null||r.provider()==null||r.provider().isBlank()||r.reference()==null||r.reference().isBlank()||r.distanceMeters()<=0||r.durationSeconds()<=0||r.calculatedAt()==null)throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_ROUTE","A resposta de rota não contém distância/duração válidas.");}
    private void expireQuotes(UUID order){jdbc.update("UPDATE orcamento SET estado='EXPIRADO',version=version+1 WHERE pedido_id=? AND estado='PROPOSTO' AND expira_em<=clock_timestamp()",order);}
    private void authorizePedidos(UUID actor,UUID patient){if(jdbc.queryForObject("SELECT count(*) FROM paciente WHERE id=? AND usuario_id=?",Long.class,patient,actor)==1)return;long n=jdbc.queryForObject("SELECT count(*) FROM autorizacao_paciente a JOIN autorizacao_escopo e ON e.autorizacao_id=a.id WHERE a.paciente_id=? AND a.familiar_id=? AND a.revogada_em IS NULL AND a.expira_em>clock_timestamp() AND e.escopo='PEDIDOS'",Long.class,patient,actor);if(n!=1)throw notFound();}
    private void authorizeRecipient(UUID patient,UUID recipient){UUID owner=jdbc.queryForObject("SELECT usuario_id FROM paciente WHERE id=?",UUID.class,patient);if(owner.equals(recipient))return;long n=jdbc.queryForObject("SELECT count(*) FROM autorizacao_paciente a JOIN autorizacao_escopo e ON e.autorizacao_id=a.id WHERE a.paciente_id=? AND a.familiar_id=? AND a.revogada_em IS NULL AND a.expira_em>clock_timestamp() AND e.escopo='RECEBIMENTO'",Long.class,patient,recipient);if(n!=1)throw error(HttpStatus.UNPROCESSABLE_ENTITY,"RECIPIENT_UNAUTHORIZED","Destinatário não possui RECEBIMENTO vigente.");}
    private void requireOwnedAuthorization(UUID actor,UUID doc){try{Map<String,Object> d=jdbc.queryForMap("SELECT proprietario_id,categoria,estado FROM documento WHERE id=?",doc);if(!actor.equals(d.get("proprietario_id"))||!"AUTORIZACAO_RETIRADA".equals(d.get("categoria"))||List.of("REJEITADO","EXPURGADO").contains(d.get("estado")))throw notFound();}catch(EmptyResultDataAccessException e){throw notFound();}}
    private void authorizeRead(UUID actor,UUID id){Map<String,Object> r=row(id);UUID patientUser=jdbc.queryForObject("SELECT usuario_id FROM paciente WHERE id=?",UUID.class,r.get("paciente_id"));if(patientUser.equals(actor))return;long n=jdbc.queryForObject("SELECT count(*) FROM autorizacao_paciente a JOIN autorizacao_escopo e ON e.autorizacao_id=a.id WHERE a.paciente_id=? AND a.familiar_id=? AND a.revogada_em IS NULL AND a.expira_em>clock_timestamp() AND e.escopo='PEDIDOS'",Long.class,r.get("paciente_id"),actor);if(n!=1)throw notFound();}
    private Map<String,Object> authorization(UUID id){return jdbc.queryForMap("SELECT * FROM autorizacao_retirada WHERE pedido_id=?",id);}
    private Map<String,Object> row(UUID id){try{return jdbc.queryForMap("SELECT * FROM pedido WHERE id=?",id);}catch(EmptyResultDataAccessException e){throw notFound();}}
    private Map<String,Object> forUpdate(UUID id){try{return jdbc.queryForMap("SELECT * FROM pedido WHERE id=? FOR UPDATE",id);}catch(EmptyResultDataAccessException e){throw notFound();}}
    private Map<String,Object> quoteRow(UUID id){try{return jdbc.queryForMap("SELECT * FROM orcamento WHERE id=?",id);}catch(EmptyResultDataAccessException e){throw notFound();}}
    private OrderView order(UUID id){Map<String,Object> r=row(id);Map<String,Object> a=authorization(id);return new OrderView(id,(UUID)r.get("paciente_id"),(String)r.get("estado"),"NAO_CONTRATADA",((java.sql.Timestamp)r.get("created_at")).toInstant(),((Number)r.get("version")).longValue(),(UUID)a.get("id"),(UUID)r.get("unidade_id"));}
    private QuoteView quote(UUID id){Map<String,Object> r=quoteRow(id);int tariffVersion=jdbc.queryForObject("SELECT numero FROM tarifa WHERE id=?",Integer.class,r.get("tarifa_id"));return new QuoteView(id,(UUID)r.get("pedido_id"),(String)r.get("estado"),(BigDecimal)r.get("frete"),(BigDecimal)r.get("paciente_valor"),(BigDecimal)r.get("subsidio_valor"),tariffVersion,(String)r.get("rota_provedor"),(String)r.get("rota_referencia"),((Number)r.get("distancia_m")).intValue(),((Number)r.get("duracao_s")).intValue(),(Boolean)r.get("transito_incluido"),((java.sql.Timestamp)r.get("rota_calculada_em")).toInstant(),((java.sql.Timestamp)r.get("expira_em")).toInstant(),((Number)r.get("version")).longValue());}
    private Address read(byte[] value){try{return json.readValue(protector.decrypt(value),Address.class);}catch(Exception e){throw error(HttpStatus.INTERNAL_SERVER_ERROR,"DATA_UNAVAILABLE","Endereço protegido indisponível.");}}
    private byte[] protect(Address a){try{return protector.encrypt(json.writeValueAsString(a));}catch(JsonProcessingException e){throw error(HttpStatus.INTERNAL_SERVER_ERROR,"DATA_UNAVAILABLE","Endereço protegido indisponível.");}}
    private byte[] hash(Object input){try{return MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(input));}catch(Exception e){throw new IllegalStateException(e);}}
    private UUID replay(UUID actor,String op,String key,byte[] hash){if(key==null)return null;try{Map<String,Object> r=jdbc.queryForMap("SELECT request_hash,pedido_id FROM pedido_idempotencia WHERE ator_id=? AND operacao=? AND chave=?",actor,op,key);if(!MessageDigest.isEqual((byte[])r.get("request_hash"),hash))throw error(HttpStatus.CONFLICT,"IDEMPOTENCY_CONFLICT","A chave já foi usada com dados diferentes.");return (UUID)r.get("pedido_id");}catch(EmptyResultDataAccessException e){return null;}}
    private UUID replayQuote(UUID actor,String key,byte[] hash){if(key==null)return null;try{Map<String,Object> r=jdbc.queryForMap("SELECT request_hash,orcamento_id FROM pedido_idempotencia WHERE ator_id=? AND operacao='CREATE_QUOTE' AND chave=?",actor,key);if(!MessageDigest.isEqual((byte[])r.get("request_hash"),hash))throw error(HttpStatus.CONFLICT,"IDEMPOTENCY_CONFLICT","A chave já foi usada com dados diferentes.");return (UUID)r.get("orcamento_id");}catch(EmptyResultDataAccessException e){return null;}}
    private void requireVersion(Map<String,Object> r,long v){if(((Number)r.get("version")).longValue()!=v)throw error(HttpStatus.PRECONDITION_FAILED,"VERSION_MISMATCH","O pedido mudou; recarregue antes de cotar.");}
    private void requireAddress(Address a){if(a.latitude()==null||a.longitude()==null||!a.postalCode().matches("\\d{8}"))throw error(HttpStatus.BAD_REQUEST,"INVALID_ADDRESS","Endereço ou coordenadas inválidos.");}
    private static OrderException notFound(){return error(HttpStatus.NOT_FOUND,"NOT_FOUND","Pedido não encontrado.");}
    static OrderException error(HttpStatus s,String c,String m){return new OrderException(s,c,m);}
    static final class OrderException extends org.springframework.web.server.ResponseStatusException {private final ApiError body;OrderException(HttpStatus s,String c,String m){super(s,m);body=new ApiError(c,m,UUID.randomUUID(),s.is5xxServerError());}ApiError body(){return body;}}
}
