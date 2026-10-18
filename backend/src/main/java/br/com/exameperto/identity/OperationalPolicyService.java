package br.com.exameperto.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class OperationalPolicyService {
  private final JdbcTemplate jdbc;
  private final MfaService mfa;
  private final ObjectMapper json;
  private final CustodyService custody;
  OperationalPolicyService(JdbcTemplate jdbc, MfaService mfa, ObjectMapper json, CustodyService custody) { this.jdbc=jdbc; this.mfa=mfa; this.json=json; this.custody=custody; }

  List<OperationalIncidentView> incidents(AuthService.SessionPrincipal p){ return custody.operationalIncidents(p); }
  IncidentView resolve(AuthService.SessionPrincipal p,UUID id,IncidentResolutionInput input){ return custody.resolveIncident(p,id,input); }

  List<OperationalPolicyView> list(AuthService.SessionPrincipal p) {
    elevated(p);
    return jdbc.query("SELECT id,'TARIFA',numero,versao,escopo,estado,inicio,fim,publicada_em,desativada_em,usada_em IS NOT NULL FROM tarifa UNION ALL SELECT id,'CANCELAMENTO',numero,versao,escopo,estado,inicio,fim,publicada_em,desativada_em,usada_em IS NOT NULL FROM politica_cancelamento UNION ALL SELECT id,'DESIGNACAO',numero,versao,escopo,estado,inicio,fim,publicada_em,desativada_em,usada_em IS NOT NULL FROM politica_designacao ORDER BY 2,3 DESC", (rs,n)->view(rs));
  }

  OperationalPolicyDetailView detail(AuthService.SessionPrincipal p,String type,UUID id) {
    elevated(p); String table=table(type); Map<String,Object> row=policyRow(table,id);
    Map<String,Object> values;
    try {
      if ("TARIFA".equals(type)) values=json.readValue(String.valueOf(row.get("parametros")),Map.class);
      else if ("CANCELAMENTO".equals(type)) values=json.readValue(String.valueOf(row.get("criterios")),Map.class);
      else values=Map.of("simultaneousTasks",row.get("limite_tarefas_simultaneas"));
    } catch(Exception ex){ throw error("INVALID_POLICY","Os valores da política não puderam ser lidos."); }
    return new OperationalPolicyDetailView(type.equals("TARIFA")?tariff(id):type.equals("CANCELAMENTO")?cancellation(id):designation(id),values);
  }

  List<OperationalPolicyHistoryView> history(AuthService.SessionPrincipal p,String type,UUID id) {
    elevated(p); policyRow(table(type),id);
    return jdbc.query("SELECT acao,versao,escopo,created_at FROM auditoria_politica_operacional WHERE tipo=? AND politica_id=? ORDER BY created_at,id",(rs,n)->new OperationalPolicyHistoryView(rs.getString(1),rs.getInt(2),rs.getString(3),rs.getTimestamp(4).toInstant()),type,id);
  }

  @Transactional
  OperationalPolicyView createTariff(AuthService.SessionPrincipal p, TariffPolicyInput in) {
    elevated(p); validate(in); lockScope("TARIFA", in.scope());
    int number = nextNumber("tarifa"); UUID id=UUID.randomUUID();
    String parameters = parameters(in);
    jdbc.update("INSERT INTO tarifa(id,numero,formula_codigo,parametros,inicio,fim,estado,versao,escopo,responsavel_id) VALUES (?,?, 'BASE_KM_MINUTO',?::jsonb,?,?,'RASCUNHO',1,?,?)", id,number,parameters,Timestamp.from(in.startsAt()),in.endsAt()==null?null:Timestamp.from(in.endsAt()),in.scope().trim(),p.userId());
    audit("TARIFA",id,1,in.scope(),"CRIADA",p.userId(),parameters);
    return tariff(id);
  }

  @Transactional
  OperationalPolicyView editTariff(AuthService.SessionPrincipal p,UUID id,TariffPolicyInput in) {
    elevated(p); validate(in); Map<String,Object> row=tariffRow(id); String state=String.valueOf(row.get("estado"));
    if ("RASCUNHO".equals(state)) {
      jdbc.update("UPDATE tarifa SET parametros=?::jsonb,inicio=?,fim=?,escopo=?,responsavel_id=? WHERE id=? AND estado='RASCUNHO'",parameters(in),Timestamp.from(in.startsAt()),in.endsAt()==null?null:Timestamp.from(in.endsAt()),in.scope().trim(),p.userId(),id);
      audit("TARIFA",id,((Number)row.get("versao")).intValue(),in.scope(),"EDITADA",p.userId(),parameters(in)); return tariff(id);
    }
    if (!"ATIVA".equals(state)) throw error("POLICY_NOT_EDITABLE","Somente rascunhos ou políticas publicadas podem gerar uma nova versão.");
    lockScope("TARIFA",in.scope()); int version=((Number)row.get("versao")).intValue()+1; UUID next=UUID.randomUUID(); String parameters=parameters(in);
    jdbc.update("INSERT INTO tarifa(id,numero,formula_codigo,parametros,inicio,fim,estado,versao,escopo,responsavel_id) VALUES (?,?, 'BASE_KM_MINUTO',?::jsonb,?,?,'RASCUNHO',?,?,?)",next,row.get("numero"),parameters,Timestamp.from(in.startsAt()),in.endsAt()==null?null:Timestamp.from(in.endsAt()),version,in.scope().trim(),p.userId());
    audit("TARIFA",next,version,in.scope(),"NOVA_VERSAO",p.userId(),parameters); return tariff(next);
  }

  @Transactional
  OperationalPolicyView publishTariff(AuthService.SessionPrincipal p, UUID id) {
    elevated(p); Map<String,Object> row=tariffRow(id); lockScope("TARIFA",String.valueOf(row.get("escopo")));
    if (!"RASCUNHO".equals(row.get("estado"))) throw error("POLICY_NOT_DRAFT","Somente uma política em rascunho pode ser publicada.");
    overlap("tarifa",id,row); jdbc.update("UPDATE tarifa SET estado='ATIVA',publicada_em=clock_timestamp(),responsavel_id=? WHERE id=?",p.userId(),id);
    audit("TARIFA",id,((Number)row.get("versao")).intValue(),String.valueOf(row.get("escopo")),"PUBLICADA",p.userId(),String.valueOf(row.get("parametros")));
    return tariff(id);
  }

  @Transactional
  OperationalPolicyView deactivateTariff(AuthService.SessionPrincipal p, UUID id) {
    elevated(p); Map<String,Object> row=tariffRow(id); if(!"ATIVA".equals(row.get("estado"))) throw error("POLICY_NOT_ACTIVE","A política não está publicada.");
    jdbc.update("UPDATE tarifa SET estado='INATIVA',desativada_em=clock_timestamp(),fim=coalesce(fim,clock_timestamp()) WHERE id=?",id);
    audit("TARIFA",id,((Number)row.get("versao")).intValue(),String.valueOf(row.get("escopo")),"DESATIVADA",p.userId(),"{}"); return tariff(id);
  }

  @Transactional OperationalPolicyView createCancellation(AuthService.SessionPrincipal p, CancellationPolicyInput in) { elevated(p); if(!in.validWindow()) throw error("INVALID_INPUT","Vigência inválida."); return createCancellationRow(p,in); }
  @Transactional OperationalPolicyView createDesignation(AuthService.SessionPrincipal p, DesignationPolicyInput in) { elevated(p); if(!in.validWindow()) throw error("INVALID_INPUT","Vigência inválida."); int number=nextNumber("politica_designacao"); UUID id=UUID.randomUUID(); jdbc.update("INSERT INTO politica_designacao(id,numero,limite_tarefas_simultaneas,inicio,fim,estado,versao,escopo,responsavel_id) VALUES (?,?,?,?,?,'RASCUNHO',1,?,?)",id,number,in.simultaneousTasks(),Timestamp.from(in.startsAt()),in.endsAt()==null?null:Timestamp.from(in.endsAt()),in.scope().trim(),p.userId()); audit("DESIGNACAO",id,1,in.scope(),"CRIADA",p.userId(),"{}"); return designation(id); }
  @Transactional OperationalPolicyView publish(AuthService.SessionPrincipal p, String type, UUID id) { elevated(p); String table=table(type); Map<String,Object> row=policyRow(table,id); lockScope(type,String.valueOf(row.get("escopo"))); if(!"RASCUNHO".equals(row.get("estado"))) throw error("POLICY_NOT_DRAFT","Somente rascunho pode ser publicado."); overlap(table,id,row); jdbc.update("UPDATE "+table+" SET estado='ATIVA',publicada_em=clock_timestamp(),responsavel_id=? WHERE id=?",p.userId(),id); audit(type,id,((Number)row.get("versao")).intValue(),String.valueOf(row.get("escopo")),"PUBLICADA",p.userId(),"{}"); return type.equals("CANCELAMENTO")?cancellation(id):designation(id); }
  @Transactional OperationalPolicyView deactivate(AuthService.SessionPrincipal p, String type, UUID id) { elevated(p); String table=table(type); Map<String,Object> row=policyRow(table,id); if(!"ATIVA".equals(row.get("estado"))) throw error("POLICY_NOT_ACTIVE","A política não está publicada."); jdbc.update("UPDATE "+table+" SET estado='INATIVA',desativada_em=clock_timestamp(),fim=coalesce(fim,clock_timestamp()) WHERE id=?",id); audit(type,id,((Number)row.get("versao")).intValue(),String.valueOf(row.get("escopo")),"DESATIVADA",p.userId(),"{}"); return type.equals("CANCELAMENTO")?cancellation(id):designation(id); }

  private void elevated(AuthService.SessionPrincipal p){ if(jdbc.queryForObject("SELECT count(*) FROM papel_global WHERE usuario_id=? AND papel='ANALISTA_OPERACIONAL' AND revogado_em IS NULL",Long.class,p.userId())==0) throw error("FORBIDDEN","Área administrativa não autorizada."); mfa.requireVerified(p); }
  private void validate(TariffPolicyInput in){ if(!in.validWindow()||in.roundingScale()!=2||in.maximumDistanceMeters()>6_000_000||in.quoteValiditySeconds()>604800||in.acceptanceDeadlineSeconds()>604800||in.executionDeadlineSeconds()>2592000||Stream.of(in.baseAmount(),in.perKmAmount(),in.perMinuteAmount(),in.minimumAmount(),in.returnAmount()).anyMatch(v -> v.scale()>2)) throw error("INVALID_INPUT","Vigência, valores em centavos, arredondamento ou limites inválidos."); }
  private String parameters(TariffPolicyInput in){try{return json.writeValueAsString(Map.ofEntries(Map.entry("base",in.baseAmount().toPlainString()),Map.entry("perKm",in.perKmAmount().toPlainString()),Map.entry("perMinute",in.perMinuteAmount().toPlainString()),Map.entry("minimum",in.minimumAmount().toPlainString()),Map.entry("roundingScale",in.roundingScale()),Map.entry("maximumDistanceMeters",in.maximumDistanceMeters()),Map.entry("validitySeconds",in.quoteValiditySeconds()),Map.entry("acceptanceDeadlineSeconds",in.acceptanceDeadlineSeconds()),Map.entry("executionDeadlineSeconds",in.executionDeadlineSeconds()),Map.entry("returnAmount",in.returnAmount().toPlainString()),Map.entry("returnCoverage",in.returnCoverage().trim())));}catch(Exception e){throw new IllegalStateException(e);}}
  private int nextNumber(String table){return jdbc.queryForObject("SELECT coalesce(max(numero),0)+1 FROM "+table,Integer.class);}
  private OperationalPolicyView createCancellationRow(AuthService.SessionPrincipal p,CancellationPolicyInput in){int number=nextNumber("politica_cancelamento");UUID id=UUID.randomUUID();try{String criteria=json.writeValueAsString(Map.of("beforeDesignation",in.beforeDesignation(),"afterDesignation",in.afterDesignation(),"afterPickup",in.afterPickup(),"refundHandling",in.refundHandling()));jdbc.update("INSERT INTO politica_cancelamento(id,numero,criterios,inicio,fim,estado,versao,escopo,responsavel_id) VALUES (?,?,?::jsonb,?,?,'RASCUNHO',1,?,?)",id,number,criteria,Timestamp.from(in.startsAt()),in.endsAt()==null?null:Timestamp.from(in.endsAt()),in.scope().trim(),p.userId());audit("CANCELAMENTO",id,1,in.scope(),"CRIADA",p.userId(),criteria);return cancellation(id);}catch(Exception e){throw new IllegalStateException(e);}}
  private String table(String type){return switch(type){case "TARIFA"->"tarifa";case "CANCELAMENTO"->"politica_cancelamento";case "DESIGNACAO"->"politica_designacao";default->throw error("INVALID_INPUT","Tipo de política inválido.");};}
  private Map<String,Object> policyRow(String table,UUID id){try{return jdbc.queryForMap("SELECT * FROM "+table+" WHERE id=?",id);}catch(EmptyResultDataAccessException e){throw error("NOT_FOUND","Política não encontrada.");}}
  private OperationalPolicyView cancellation(UUID id){return jdbc.queryForObject("SELECT id,numero,versao,escopo,estado,inicio,fim,publicada_em,desativada_em,usada_em IS NOT NULL FROM politica_cancelamento WHERE id=?",(rs,n)->view(rs,"CANCELAMENTO"),id);}
  private OperationalPolicyView designation(UUID id){return jdbc.queryForObject("SELECT id,numero,versao,escopo,estado,inicio,fim,publicada_em,desativada_em,usada_em IS NOT NULL FROM politica_designacao WHERE id=?",(rs,n)->view(rs,"DESIGNACAO"),id);}
  private void lockScope(String type,String scope){jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",(rs,n)->0,type+":"+scope.trim());}
  private void overlap(String table,UUID id,Map<String,Object> r){String sql="SELECT count(*) FROM "+table+" WHERE id<>? AND estado='ATIVA' AND escopo=? AND inicio < coalesce(?::timestamptz,'infinity'::timestamptz) AND coalesce(fim,'infinity'::timestamptz) > ?::timestamptz"; if(jdbc.queryForObject(sql,Long.class,id,r.get("escopo"),r.get("fim"),r.get("inicio"))>0) throw error("POLICY_OVERLAP","A vigência sobrepõe outra política publicada no mesmo escopo.");}
  private Map<String,Object> tariffRow(UUID id){try{return jdbc.queryForMap("SELECT * FROM tarifa WHERE id=?",id);}catch(EmptyResultDataAccessException e){throw error("NOT_FOUND","Política tarifária não encontrada.");}}
  private OperationalPolicyView tariff(UUID id){return jdbc.queryForObject("SELECT id,numero,versao,escopo,estado,inicio,fim,publicada_em,desativada_em,usada_em IS NOT NULL FROM tarifa WHERE id=?",(rs,n)->view(rs,"TARIFA"),id);}
  private OperationalPolicyView view(java.sql.ResultSet rs) throws java.sql.SQLException{return new OperationalPolicyView(rs.getObject(1,UUID.class),rs.getString(2),rs.getInt(3),rs.getInt(4),rs.getString(5),rs.getString(6),rs.getTimestamp(7).toInstant(),rs.getTimestamp(8)==null?null:rs.getTimestamp(8).toInstant(),rs.getTimestamp(9)==null?null:rs.getTimestamp(9).toInstant(),rs.getTimestamp(10)==null?null:rs.getTimestamp(10).toInstant(),rs.getBoolean(11));}
  private OperationalPolicyView view(java.sql.ResultSet rs,String type) throws java.sql.SQLException{return new OperationalPolicyView(rs.getObject(1,UUID.class),type,rs.getInt(2),rs.getInt(3),rs.getString(4),rs.getString(5),rs.getTimestamp(6).toInstant(),rs.getTimestamp(7)==null?null:rs.getTimestamp(7).toInstant(),rs.getTimestamp(8)==null?null:rs.getTimestamp(8).toInstant(),rs.getTimestamp(9)==null?null:rs.getTimestamp(9).toInstant(),rs.getBoolean(10));}
  private void audit(String type,UUID id,int version,String scope,String action,UUID actor,String details){jdbc.update("INSERT INTO auditoria_politica_operacional(id,tipo,politica_id,versao,escopo,acao,ator_id,detalhes) VALUES (?,?,?,?,?,?,?,?::jsonb)",UUID.randomUUID(),type,id,version,scope,action,actor,details);}
  private RuntimeException error(String code,String message){return new PolicyException("FORBIDDEN".equals(code)?HttpStatus.FORBIDDEN:HttpStatus.UNPROCESSABLE_ENTITY,code,message);}
  static class PolicyException extends RuntimeException { final HttpStatus status; final ApiError body; PolicyException(HttpStatus s,String c,String m){super(m);status=s;body=new ApiError(c,m,UUID.randomUUID(),false);} }
}
