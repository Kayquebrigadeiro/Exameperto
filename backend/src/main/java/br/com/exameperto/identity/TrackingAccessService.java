package br.com.exameperto.identity;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
class TrackingAccessService {
    private final JdbcTemplate jdbc;
    TrackingAccessService(JdbcTemplate jdbc){this.jdbc=jdbc;}

    Map<String,Object> activeTask(UUID orderId){
        try{return jdbc.queryForMap("SELECT p.id pedido_id,p.estado,d.id designacao_id,e.usuario_id entregador_id FROM pedido p JOIN designacao d ON d.pedido_id=p.id AND d.encerrada_em IS NULL JOIN entregador e ON e.id=d.entregador_id JOIN usuario u ON u.id=e.usuario_id AND u.estado='ATIVO' WHERE p.id=? AND p.estado IN ('ACEITA','RETIRADA','EM_ENTREGA','OCORRENCIA') AND (p.estado<>'OCORRENCIA' OR EXISTS (SELECT 1 FROM custodia c WHERE c.pedido_id=p.id AND c.encerrada_em IS NULL))",orderId);}catch(org.springframework.dao.EmptyResultDataAccessException ex){throw error(HttpStatus.NOT_FOUND,"TRACKING_INACTIVE","Rastreamento indisponível para tarefa inativa.");}
    }
    void requirePublisher(UUID actor,UUID orderId,UUID assignmentId){
        try{jdbc.queryForObject("SELECT id FROM pedido WHERE id=? FOR UPDATE",UUID.class,orderId);}catch(org.springframework.dao.EmptyResultDataAccessException ex){throw error(HttpStatus.NOT_FOUND,"NOT_FOUND","Tarefa não encontrada.");}
        Map<String,Object> task=activeTask(orderId);if(!actor.equals(task.get("entregador_id"))||!assignmentId.equals(task.get("designacao_id")))throw error(HttpStatus.NOT_FOUND,"NOT_FOUND","Tarefa não encontrada.");
        jdbc.queryForObject("SELECT id FROM designacao WHERE id=? FOR UPDATE",UUID.class,assignmentId);
    }
    void requireViewer(UUID actor,UUID orderId){if(!canView(actor,orderId))throw error(HttpStatus.NOT_FOUND,"NOT_FOUND","Tarefa não encontrada.");}
    boolean canView(UUID actor,UUID orderId){
        try{Map<String,Object> task=activeTask(orderId);if(actor.equals(task.get("entregador_id")))return true;
            return jdbc.queryForObject("SELECT count(*) FROM pedido p JOIN paciente pt ON pt.id=p.paciente_id LEFT JOIN autorizacao_paciente a ON a.paciente_id=p.paciente_id AND a.familiar_id=? AND a.revogada_em IS NULL AND a.expira_em>clock_timestamp() LEFT JOIN autorizacao_escopo ae ON ae.autorizacao_id=a.id AND ae.escopo='RASTREAMENTO' WHERE p.id=? AND (pt.usuario_id=? OR ae.autorizacao_id IS NOT NULL)",Long.class,actor,orderId,actor)>0;
        }catch(OrderService.OrderException ex){return false;}
    }
    boolean sessionActive(UUID user,UUID session){return jdbc.queryForObject("SELECT count(*) FROM sessao s JOIN usuario u ON u.id=s.usuario_id WHERE s.id=? AND s.usuario_id=? AND s.revogada_em IS NULL AND s.access_expira_em>clock_timestamp() AND s.expira_em>clock_timestamp() AND u.estado='ATIVO'",Long.class,session,user)==1;}
    static OrderService.OrderException error(HttpStatus status,String code,String message){return OrderService.error(status,code,message);}
}
