package br.com.exameperto.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ConfiguredRouteProviderTest {
    private HttpServer server;

    @AfterEach void stop() { if (server != null) server.stop(0); }

    @Test void parsesDrivingRouteInMetersAndSecondsAndDoesNotClaimTraffic() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        server = server(exchange -> { calls.incrementAndGet(); respond(exchange, 200, "{\"routes\":[{\"summary\":{\"distance\":12500.4,\"duration\":1800.6}}]}"); });
        RouteProvider.RouteResult result = provider(Duration.ofSeconds(2), 1).route(address(-23.55, -46.63), address(-22.90, -43.17));

        assertThat(result.provider()).isEqualTo("openrouteservice");
        assertThat(result.reference()).startsWith("ors:").hasSize(68);
        assertThat(result.distanceMeters()).isEqualTo(12500);
        assertThat(result.durationSeconds()).isEqualTo(1801);
        assertThat(result.trafficIncluded()).isFalse();
        assertThat(calls).hasValue(1);
    }

    @Test void rejectsInvalidResponseAndRouteNotFound() throws Exception {
        server = server(exchange -> respond(exchange, 200, "{\"routes\":[{\"summary\":{\"distance\":0,\"duration\":10}}]"));
        assertThatThrownBy(() -> provider(Duration.ofSeconds(2), 0).route(address(-23.5, -46.6), address(-23.4, -46.5)))
                .isInstanceOf(OrderService.OrderException.class).hasMessageContaining("distância/duração");

        server.stop(0);
        server = server(exchange -> respond(exchange, 404, "{}"));
        assertThatThrownBy(() -> provider(Duration.ofSeconds(2), 0).route(address(-23.5, -46.6), address(-23.4, -46.5)))
                .isInstanceOf(OrderService.OrderException.class).hasMessageContaining("encontrar uma rota");
    }

    @Test void treatsTimeoutRateLimitAndOversizedBodyAsUnavailableOrInvalid() throws Exception {
        server = server(exchange -> respond(exchange, 429, "{}"));
        assertThatThrownBy(() -> provider(Duration.ofSeconds(2), 1).route(address(-23.5, -46.6), address(-23.4, -46.5)))
                .isInstanceOf(OrderService.OrderException.class).hasMessageContaining("disponível");

        server.stop(0);
        server = server(exchange -> respond(exchange, 200, "x".repeat(2048)));
        assertThatThrownBy(() -> provider(Duration.ofSeconds(2), 0).route(address(-23.5, -46.6), address(-23.4, -46.5)))
                .isInstanceOf(OrderService.OrderException.class).hasMessageContaining("excedeu o limite");
    }

    @Test void rejectsMissingCoordinatesBeforeCallingProvider() throws Exception {
        server = server(exchange -> respond(exchange, 200, "{}"));
        assertThatThrownBy(() -> provider(Duration.ofSeconds(2), 0).route(address(null, -46.6), address(-23.4, -46.5)))
                .isInstanceOf(OrderService.OrderException.class).hasMessageContaining("coordenadas confirmadas");
    }

    @Test void turnsProviderTimeoutIntoUnavailableWithoutFallback() throws Exception {
        server = server(exchange -> {
            try { Thread.sleep(200); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            respond(exchange, 200, "{\"routes\":[{\"summary\":{\"distance\":100,\"duration\":10}}]}");
        });
        assertThatThrownBy(() -> provider(Duration.ofMillis(50), 0).route(address(-23.5, -46.6), address(-23.4, -46.5)))
                .isInstanceOf(OrderService.OrderException.class).hasMessageContaining("não respondeu");
    }

    private ConfiguredRouteProvider provider(Duration timeout, int retries) {
        return new ConfiguredRouteProvider(true, "test-key", URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/route"), timeout, 1024, retries, new ObjectMapper());
    }

    private HttpServer server(com.sun.net.httpserver.HttpHandler handler) throws Exception {
        HttpServer value = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        value.createContext("/route", handler);
        value.start();
        return value;
    }

    private static Address address(double latitude, double longitude) { return address(BigDecimal.valueOf(latitude), BigDecimal.valueOf(longitude)); }
    private static Address address(BigDecimal latitude, double longitude) { return address(latitude, BigDecimal.valueOf(longitude)); }
    private static Address address(BigDecimal latitude, BigDecimal longitude) { return new Address("Rua", "1", "Centro", "São Paulo", "SP", "01001000", latitude, longitude); }
    private static void respond(HttpExchange exchange, int status, String body) throws java.io.IOException { byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8); exchange.sendResponseHeaders(status, bytes.length); try (var output = exchange.getResponseBody()) { output.write(bytes); } }
}
