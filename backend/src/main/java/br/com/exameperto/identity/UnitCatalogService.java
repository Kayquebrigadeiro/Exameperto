package br.com.exameperto.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
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
class UnitCatalogService {
    private final JdbcTemplate jdbc;
    private final MfaService mfa;
    private final ObjectMapper json;
    UnitCatalogService(JdbcTemplate jdbc, MfaService mfa, ObjectMapper json) { this.jdbc=jdbc; this.mfa=mfa; this.json=json; }

    List<UnitCatalogView> list(AuthService.SessionPrincipal principal) { require(principal.userId()); return jdbc.query("SELECT id FROM unidade_retirada ORDER BY nome,cidade,id", (rs,n)->unit(rs.getObject(1,UUID.class))); }

    List<AvailableUnitView> available() {
        return jdbc.query("SELECT u.id,u.nome,u.cidade,u.estado FROM unidade_retirada u JOIN aceite_unidade_retirada a ON a.unidade_id=u.id AND a.estado='ACEITA' JOIN protocolo_custodia p ON p.unidade_id=u.id AND p.estado='HABILITADO' AND p.cobertura_retorno_confirmada AND p.inicio<=clock_timestamp() AND (p.fim IS NULL OR p.fim>clock_timestamp()) WHERE u.estado_cadastro='ATIVA' ORDER BY u.nome,u.cidade,u.id", (rs,n)->new AvailableUnitView(rs.getObject(1,UUID.class),rs.getString(2)+" · "+rs.getString(3)+"/"+rs.getString(4)));
    }

    @Transactional UnitCatalogView create(AuthService.SessionPrincipal p, UnitCatalogInput input) { elevated(p); UUID id=UUID.randomUUID(); String state=input.state().trim().toUpperCase(); validateState(state); jdbc.update("INSERT INTO unidade_retirada(id,nome,cidade,estado,estado_cadastro,criado_por,atualizado_por) VALUES (?,?,?,?, 'CADASTRADA',?,?)",id,input.name().trim(),input.city().trim(),state,p.userId(),p.userId()); audit(id,null,p.userId(),"UNIDADE_CRIADA",Map.of("status","CADASTRADA")); return unit(id); }

    @Transactional UnitCatalogView edit(AuthService.SessionPrincipal p, UUID id, UnitCatalogInput input) { elevated(p); unitRow(id); String state=input.state().trim().toUpperCase(); validateState(state); jdbc.update("UPDATE unidade_retirada SET nome=?,cidade=?,estado=?,updated_at=clock_timestamp(),atualizado_por=?,version=version+1 WHERE id=?",input.name().trim(),input.city().trim(),state,p.userId(),id); audit(id,null,p.userId(),"UNIDADE_EDITADA",Map.of()); return unit(id); }

    @Transactional UnitCatalogView setRegistration(AuthService.SessionPrincipal p, UUID id, boolean active) { elevated(p); unitRow(id); jdbc.update("UPDATE unidade_retirada SET estado_cadastro=?,updated_at=clock_timestamp(),atualizado_por=?,version=version+1 WHERE id=?",active?"ATIVA":"DESATIVADA",p.userId(),id); if(!active) jdbc.update("UPDATE protocolo_custodia SET estado='SUSPENSO',fim=coalesce(fim,clock_timestamp()) WHERE unidade_id=? AND estado='HABILITADO'",id); audit(id,null,p.userId(),active?"UNIDADE_ATIVADA":"UNIDADE_DESATIVADA",Map.of()); return unit(id); }

    @Transactional UnitCatalogView partnership(AuthService.SessionPrincipal p, UUID id, PartnershipInput input) { elevated(p); unitRow(id); if(!List.of("PENDENTE","ACEITA","REVOGADA").contains(input.status())) throw error("INVALID_INPUT","Estado de parceria inválido."); if("ACEITA".equals(input.status()) && input.evidenceReference().isBlank()) throw error("INVALID_INPUT","A aceitação exige a referência da comprovação recebida; nenhum parceiro foi presumido."); jdbc.update("UPDATE aceite_unidade_retirada SET estado='REVOGADA' WHERE unidade_id=? AND estado IN ('PENDENTE','ACEITA')",id); UUID aid=UUID.randomUUID(); jdbc.update("INSERT INTO aceite_unidade_retirada(id,unidade_id,estado,evidencia_referencia,observacao,aceito_por,aceito_em,criado_por) VALUES (?,?,?,?,?,?,?,?)",aid,id,input.status(),input.evidenceReference().trim(),input.note(),"ACEITA".equals(input.status())?p.userId():null,"ACEITA".equals(input.status())?Timestamp.from(Instant.now()):null,p.userId()); audit(id,null,p.userId(),"ACEITA".equals(input.status())?"PARCERIA_ACEITA":("REVOGADA".equals(input.status())?"PARCERIA_REVOGADA":"PARCERIA_REGISTRADA"),Map.of("evidenceReference",input.evidenceReference().trim())); return unit(id); }

    @Transactional UnitCatalogView createProtocol(AuthService.SessionPrincipal p, UUID id, ProtocolInput input) { elevated(p); unitRow(id); validateProtocol(input); UUID protocol=UUID.randomUUID(); jdbc.update("INSERT INTO protocolo_custodia(id,unidade_id,numero,regras,cobertura_retorno_confirmada,inicio,fim,estado) VALUES (?,?,?,?::jsonb,?,?,?,'SUSPENSO')",protocol,id,input.number(),input.rules(),input.returnCoverageConfirmed(),Timestamp.from(input.startsAt()),input.endsAt()==null?null:Timestamp.from(input.endsAt())); audit(id,protocol,p.userId(),"PROTOCOLO_CRIADO",Map.of("number",input.number())); return unit(id); }

    @Transactional UnitCatalogView editProtocol(AuthService.SessionPrincipal p, UUID id, UUID protocol, ProtocolInput input) { elevated(p); protocolRow(id,protocol); validateProtocol(input); jdbc.update("UPDATE protocolo_custodia SET numero=?,regras=?::jsonb,cobertura_retorno_confirmada=?,inicio=?,fim=? WHERE id=? AND unidade_id=?",input.number(),input.rules(),input.returnCoverageConfirmed(),Timestamp.from(input.startsAt()),input.endsAt()==null?null:Timestamp.from(input.endsAt()),protocol,id); audit(id,protocol,p.userId(),"PROTOCOLO_EDITADO",Map.of()); return unit(id); }

    @Transactional UnitCatalogView setProtocol(AuthService.SessionPrincipal p, UUID id, UUID protocol, boolean enabled) { elevated(p); protocolRow(id,protocol); if(enabled){String registration=jdbc.queryForObject("SELECT estado_cadastro FROM unidade_retirada WHERE id=?",String.class,id); if(!"ATIVA".equals(registration)) throw error("OPERATIONAL_REQUIREMENT_MISSING","Ative a unidade antes de habilitar o protocolo."); if(jdbc.queryForObject("SELECT count(*) FROM aceite_unidade_retirada WHERE unidade_id=? AND estado='ACEITA'",Long.class,id)==0) throw error("OPERATIONAL_REQUIREMENT_MISSING","Registre o aceite de parceria antes de habilitar o protocolo."); Map<String,Object> row=protocolRow(id,protocol); if(!Boolean.TRUE.equals(row.get("cobertura_retorno_confirmada"))) throw error("OPERATIONAL_REQUIREMENT_MISSING","Confirme a cobertura de retorno antes de habilitar o protocolo."); jdbc.update("UPDATE protocolo_custodia SET estado='HABILITADO' WHERE id=?",protocol); } else jdbc.update("UPDATE protocolo_custodia SET estado='SUSPENSO' WHERE id=?",protocol); audit(id,protocol,p.userId(),enabled?"PROTOCOLO_HABILITADO":"PROTOCOLO_SUSPENSO",Map.of()); return unit(id); }

    private UnitCatalogView unit(UUID id) { Map<String,Object> u=unitRow(id); PartnershipView partner; try{partner=jdbc.queryForObject("SELECT estado,evidencia_referencia,observacao,aceito_em FROM aceite_unidade_retirada WHERE unidade_id=? AND estado IN ('PENDENTE','ACEITA') ORDER BY created_at DESC LIMIT 1",(rs,n)->new PartnershipView(rs.getString(1),rs.getString(2),rs.getString(3),rs.getTimestamp(4)==null?null:rs.getTimestamp(4).toInstant()),id);}catch(EmptyResultDataAccessException ex){partner=null;} List<ProtocolView> protocols=jdbc.query("SELECT id,numero,regras::text,cobertura_retorno_confirmada,inicio,fim,estado FROM protocolo_custodia WHERE unidade_id=? ORDER BY numero,id",(rs,n)->new ProtocolView(rs.getObject(1,UUID.class),rs.getInt(2),rs.getString(3),rs.getBoolean(4),rs.getTimestamp(5).toInstant(),rs.getTimestamp(6)==null?null:rs.getTimestamp(6).toInstant(),rs.getString(7)),id); return new UnitCatalogView(id,(String)u.get("nome"),(String)u.get("cidade"),(String)u.get("estado"),(String)u.get("estado_cadastro"),partner,protocols,((Number)u.get("version")).longValue()); }
    private Map<String,Object> unitRow(UUID id){try{return jdbc.queryForMap("SELECT id,nome,cidade,estado,estado_cadastro,version FROM unidade_retirada WHERE id=?",id);}catch(EmptyResultDataAccessException ex){throw error("NOT_FOUND","Unidade não encontrada.");}}
    private Map<String,Object> protocolRow(UUID unit,UUID protocol){try{return jdbc.queryForMap("SELECT id,cobertura_retorno_confirmada FROM protocolo_custodia WHERE id=? AND unidade_id=?",protocol,unit);}catch(EmptyResultDataAccessException ex){throw error("NOT_FOUND","Protocolo não encontrado.");}}
    private void require(UUID user){if(jdbc.queryForObject("SELECT count(*) FROM papel_global WHERE usuario_id=? AND papel='ANALISTA_OPERACIONAL' AND revogado_em IS NULL",Long.class,user)==0) throw error("FORBIDDEN","Área administrativa não autorizada.");}
    private void elevated(AuthService.SessionPrincipal p){require(p.userId());mfa.requireVerified(p);}
    private void validateState(String s){if(!s.matches("[A-Z]{2}"))throw error("INVALID_INPUT","Informe um estado brasileiro com duas letras.");}
    private void validateProtocol(ProtocolInput input){if(input.number()<=0)throw error("INVALID_INPUT","O número do protocolo deve ser positivo.");try{if(!json.readTree(input.rules()).isObject())throw new IllegalArgumentException();}catch(Exception ex){throw error("INVALID_INPUT","As regras do protocolo precisam ser um objeto JSON válido.");}}
    private void audit(UUID unit,UUID protocol,UUID actor,String action,Map<String,Object> details){try{jdbc.update("INSERT INTO auditoria_unidade_retirada(id,unidade_id,protocolo_id,ator_id,acao,detalhes) VALUES (?,?,?,?,?,?::jsonb)",UUID.randomUUID(),unit,protocol,actor,action,json.writeValueAsString(details));}catch(Exception ex){throw new IllegalStateException(ex);}}
    private UnitCatalogServiceException error(String code,String message){return new UnitCatalogServiceException("FORBIDDEN".equals(code)?HttpStatus.FORBIDDEN:("NOT_FOUND".equals(code)?HttpStatus.NOT_FOUND:HttpStatus.UNPROCESSABLE_ENTITY),code,message);}
    static class UnitCatalogServiceException extends RuntimeException { final HttpStatus status; final ApiError body; UnitCatalogServiceException(HttpStatus s,String c,String m){status=s;body=new ApiError(c,m,UUID.randomUUID(),false);} }
}
