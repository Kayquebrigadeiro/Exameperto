package br.com.exameperto.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;

@Component
final class ConfiguredRouteProvider implements RouteProvider {
    private final boolean enabled;
    ConfiguredRouteProvider(@Value("${orders.route.enabled:false}") boolean enabled) { this.enabled=enabled; }
    @Override public RouteResult route(Address origin, Address destination) {
        if (!enabled) throw OrderService.error(HttpStatus.SERVICE_UNAVAILABLE,"INTEGRATION_UNAVAILABLE","Provedor de rota rodoviária não está habilitado.");
        throw OrderService.error(HttpStatus.SERVICE_UNAVAILABLE,"INTEGRATION_UNAVAILABLE","Consulta de rota não está configurada.");
    }
}
