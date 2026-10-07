package br.com.exameperto.identity;

import java.security.Principal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
class TrackingLiveRegistry {
    private final TrackingAccessService access; private final SimpMessagingTemplate messaging;
    private final Map<String,Connection> connections=new ConcurrentHashMap<>();
    TrackingLiveRegistry(TrackingAccessService access,SimpMessagingTemplate messaging){this.access=access;this.messaging=messaging;}
    void connected(String socketSession,TrackingPrincipal principal){connections.put(socketSession,new Connection(principal,ConcurrentHashMap.newKeySet()));}
    void subscribed(String socketSession,UUID orderId){Connection connection=connections.get(socketSession);if(connection!=null)connection.orders.add(orderId);}
    void disconnected(String socketSession){connections.remove(socketSession);}
    @org.springframework.context.event.EventListener void revokeAuthSession(AuthService.SessionRevokedEvent event){connections.entrySet().removeIf(entry->entry.getValue().principal.authSessionId().equals(event.sessionId()));}
    void publish(UUID orderId,LocationView location){for(var entry:connections.entrySet()){Connection connection=entry.getValue();if(!connection.orders.contains(orderId))continue;TrackingPrincipal p=connection.principal;if(!access.sessionActive(p.userId(),p.authSessionId())||!access.canView(p.userId(),orderId)){connection.orders.remove(orderId);continue;}messaging.convertAndSendToUser(p.getName(),"/queue/orders/"+orderId+"/locations",location);}}
    record Connection(TrackingPrincipal principal,Set<UUID> orders){}
    record TrackingPrincipal(UUID userId,UUID authSessionId) implements Principal {public String getName(){return userId+":"+authSessionId;}}
}
