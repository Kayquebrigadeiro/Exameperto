package br.com.exameperto.identity;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class TrackingService {
    private final JdbcTemplate jdbc; private final TrackingAccessService access; private final TrackingLiveRegistry live;
    private final boolean enabled; private final Duration maxAge, futureTolerance, staleAfter, rateWindow; private final int rateMaximum; private final BigDecimal lowAccuracy;
    TrackingService(JdbcTemplate jdbc,TrackingAccessService access,TrackingLiveRegistry live,
      @Value("${tracking.enabled:false}")boolean enabled,@Value("${tracking.max-age:PT2M}")Duration maxAge,
      @Value("${tracking.future-tolerance:PT30S}")Duration futureTolerance,@Value("${tracking.stale-after:PT60S}")Duration staleAfter,
      @Value("${tracking.rate-window:PT2M}")Duration rateWindow,@Value("${tracking.rate-maximum:8}")int rateMaximum,
      @Value("${tracking.low-accuracy-meters:100}")BigDecimal lowAccuracy){this.jdbc=jdbc;this.access=access;this.live=live;this.enabled=enabled;this.maxAge=maxAge;this.futureTolerance=futureTolerance;this.staleAfter=staleAfter;this.rateWindow=rateWindow;this.rateMaximum=rateMaximum;this.lowAccuracy=lowAccuracy;}

    @Transactional
    AcceptedLocation accept(UUID actor,UUID orderId,LocationInput input){requireEnabled();access.requirePublisher(actor,orderId,input.assignmentId());Instant now=Instant.now(),capturedAt=input.capturedAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        if(capturedAt.isBefore(now.minus(maxAge)))throw error(HttpStatus.UNPROCESSABLE_ENTITY,"LOCATION_TOO_OLD","A posição está fora da janela de envio.");
        if(capturedAt.isAfter(now.plus(futureTolerance)))throw error(HttpStatus.UNPROCESSABLE_ENTITY,"LOCATION_IN_FUTURE","O horário da posição está no futuro.");
        Map<String,Object> existing=bySequence(input.assignmentId(),input.sequence());if(existing!=null)return new AcceptedLocation(duplicate(input,capturedAt,existing),false);
        Map<String,Object> last=last(input.assignmentId());if(last!=null){long seq=((Number)last.get("sequencia")).longValue();Instant captured=((Timestamp)last.get("capturada_em")).toInstant();if(input.sequence()<seq||capturedAt.isBefore(captured))throw error(HttpStatus.UNPROCESSABLE_ENTITY,"LOCATION_OUT_OF_ORDER","A posição está fora de ordem.");}
        long recent=jdbc.queryForObject("SELECT count(*) FROM posicao_tarefa WHERE designacao_id=? AND recebida_em>clock_timestamp()-(? * interval '1 millisecond')",Long.class,input.assignmentId(),rateWindow.toMillis());if(recent>=rateMaximum)throw error(HttpStatus.TOO_MANY_REQUESTS,"LOCATION_RATE_LIMIT","Limite de posições atingido.");
        UUID id=UUID.randomUUID();try{jdbc.update("INSERT INTO posicao_tarefa(id,pedido_id,designacao_id,sequencia,capturada_em,latitude,longitude,precisao_m) VALUES (?,?,?,?,?,?,?,?)",id,orderId,input.assignmentId(),input.sequence(),Timestamp.from(capturedAt),input.latitude(),input.longitude(),input.accuracyMeters());}catch(DuplicateKeyException ex){return new AcceptedLocation(duplicate(input,capturedAt,bySequence(input.assignmentId(),input.sequence())),false);}
        LocationView view=byId(id);org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){@Override public void afterCommit(){live.publish(orderId,view);}});return new AcceptedLocation(view,true);
    }
    LocationView latest(UUID actor,UUID orderId){requireEnabled();access.requireViewer(actor,orderId);try{return jdbc.queryForObject("SELECT * FROM posicao_tarefa WHERE pedido_id=? ORDER BY capturada_em DESC,sequencia DESC LIMIT 1",(rs,n)->view(rs),orderId);}catch(EmptyResultDataAccessException ex){throw error(HttpStatus.NOT_FOUND,"LOCATION_UNAVAILABLE","Nenhuma posição foi recebida.");}}
    private LocationView duplicate(LocationInput input,Instant capturedAt,Map<String,Object> row){if(row!=null&&capturedAt.equals(((Timestamp)row.get("capturada_em")).toInstant())&&input.latitude().compareTo((BigDecimal)row.get("latitude"))==0&&input.longitude().compareTo((BigDecimal)row.get("longitude"))==0&&input.accuracyMeters().compareTo((BigDecimal)row.get("precisao_m"))==0)return byId((UUID)row.get("id"));throw error(HttpStatus.CONFLICT,"LOCATION_SEQUENCE_CONFLICT","A sequência já foi usada com outra posição.");}
    private Map<String,Object> bySequence(UUID assignment,long sequence){try{return jdbc.queryForMap("SELECT * FROM posicao_tarefa WHERE designacao_id=? AND sequencia=?",assignment,sequence);}catch(EmptyResultDataAccessException ex){return null;}}
    private Map<String,Object> last(UUID assignment){try{return jdbc.queryForMap("SELECT * FROM posicao_tarefa WHERE designacao_id=? ORDER BY sequencia DESC LIMIT 1",assignment);}catch(EmptyResultDataAccessException ex){return null;}}
    private LocationView byId(UUID id){return jdbc.queryForObject("SELECT * FROM posicao_tarefa WHERE id=?",(rs,n)->view(rs),id);}
    private LocationView view(java.sql.ResultSet rs)throws java.sql.SQLException{Instant captured=rs.getTimestamp("capturada_em").toInstant(),received=rs.getTimestamp("recebida_em").toInstant();BigDecimal accuracy=rs.getBigDecimal("precisao_m");return new LocationView(rs.getObject("designacao_id",UUID.class),rs.getLong("sequencia"),captured,received,rs.getBigDecimal("latitude"),rs.getBigDecimal("longitude"),accuracy,captured.isBefore(Instant.now().minus(staleAfter)),accuracy.compareTo(lowAccuracy)>0);}
    private void requireEnabled(){if(!enabled)throw error(HttpStatus.UNPROCESSABLE_ENTITY,"POLICY_UNDEFINED","Rastreamento aguarda política de retenção aprovada.");}
    static OrderService.OrderException error(HttpStatus s,String c,String m){return OrderService.error(s,c,m);}
    record AcceptedLocation(LocationView location,boolean created){}
}
