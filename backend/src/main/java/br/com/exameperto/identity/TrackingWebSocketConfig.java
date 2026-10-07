package br.com.exameperto.identity;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
class TrackingWebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final AuthService auth; private final TrackingAccessService access; private final org.springframework.beans.factory.ObjectProvider<TrackingLiveRegistry> live; private final String origin;
    TrackingWebSocketConfig(AuthService auth,TrackingAccessService access,org.springframework.beans.factory.ObjectProvider<TrackingLiveRegistry> live,@Value("${registration.allowed-origin:http://localhost:5173}")String origin){this.auth=auth;this.access=access;this.live=live;this.origin=origin;}
    @Override public void registerStompEndpoints(StompEndpointRegistry registry){registry.addEndpoint("/ws").setAllowedOrigins(origin);}
    @Override public void configureMessageBroker(MessageBrokerRegistry registry){registry.enableSimpleBroker("/queue");registry.setUserDestinationPrefix("/user");}
    @Override public void configureClientInboundChannel(ChannelRegistration registration){registration.interceptors(new ChannelInterceptor(){@Override public Message<?> preSend(Message<?> message,MessageChannel channel){StompHeaderAccessor h=MessageHeaderAccessor.getAccessor(message,StompHeaderAccessor.class);if(h==null||h.getCommand()==null)return message;switch(h.getCommand()){
        case CONNECT->{String header=h.getFirstNativeHeader("Authorization");if(header==null||!header.startsWith("Bearer "))throw denied();AuthService.SessionPrincipal session=auth.authenticateAccess(header.substring(7)).orElseThrow(TrackingWebSocketConfig::denied);var principal=new TrackingLiveRegistry.TrackingPrincipal(session.userId(),session.sessionId());h.setUser(principal);live.getObject().connected(h.getSessionId(),principal);}
        case SUBSCRIBE->{var principal=principal(h);UUID order=destination(h.getDestination());if(!access.sessionActive(principal.userId(),principal.authSessionId())||!access.canView(principal.userId(),order))throw denied();live.getObject().subscribed(h.getSessionId(),order);}
        case SEND->throw denied();
        case DISCONNECT->live.getObject().disconnected(h.getSessionId());
        default->{var principal=h.getUser();if(principal instanceof TrackingLiveRegistry.TrackingPrincipal p&&!access.sessionActive(p.userId(),p.authSessionId()))throw denied();}
    }return message;}});}
    private static TrackingLiveRegistry.TrackingPrincipal principal(StompHeaderAccessor h){if(h.getUser() instanceof TrackingLiveRegistry.TrackingPrincipal p)return p;throw denied();}
    private static UUID destination(String value){String prefix="/user/queue/orders/",suffix="/locations";if(value==null||!value.startsWith(prefix)||!value.endsWith(suffix)||value.contains("*")||value.contains(".."))throw denied();String id=value.substring(prefix.length(),value.length()-suffix.length());try{return UUID.fromString(id);}catch(IllegalArgumentException ex){throw denied();}}
    private static MessageDeliveryException denied(){return new MessageDeliveryException("STOMP access denied");}
}
