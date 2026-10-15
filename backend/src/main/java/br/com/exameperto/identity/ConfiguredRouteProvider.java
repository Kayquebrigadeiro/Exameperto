package br.com.exameperto.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
final class ConfiguredRouteProvider implements RouteProvider {
    private static final int MAX_COORDINATE_DISTANCE_METERS = 6_000_000;
    private final boolean enabled;
    private final String apiKey;
    private final URI endpoint;
    private final Duration timeout;
    private final int maxResponseBytes;
    private final int maxRetries;
    private final HttpClient client;
    private final ObjectMapper json;

    ConfiguredRouteProvider(
            @Value("${orders.route.enabled:false}") boolean enabled,
            @Value("${orders.route.api-key:}") String apiKey,
            @Value("${orders.route.endpoint:https://api.heigit.org/openrouteservice/v2/directions/driving-car}") URI endpoint,
            @Value("${orders.route.timeout:PT3S}") Duration timeout,
            @Value("${orders.route.max-response-bytes:65536}") int maxResponseBytes,
            @Value("${orders.route.max-retries:1}") int maxRetries,
            ObjectMapper json) {
        this.enabled = enabled;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.endpoint = endpoint;
        this.timeout = timeout;
        this.maxResponseBytes = maxResponseBytes;
        this.maxRetries = Math.max(0, Math.min(maxRetries, 1));
        this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
        this.json = json;
        if (maxResponseBytes < 1024) throw new IllegalArgumentException("orders.route.max-response-bytes must be at least 1024");
        if (!endpoint.getScheme().equalsIgnoreCase("https") && !endpoint.getHost().equals("127.0.0.1") && !endpoint.getHost().equals("localhost"))
            throw new IllegalArgumentException("orders.route.endpoint must use HTTPS outside local tests");
    }

    @Override public RouteResult route(Address origin, Address destination) {
        validateCoordinate(origin.latitude(), origin.longitude());
        validateCoordinate(destination.latitude(), destination.longitude());
        if (!enabled || apiKey.isBlank())
            throw OrderService.error(HttpStatus.SERVICE_UNAVAILABLE, "INTEGRATION_UNAVAILABLE", "Provedor de rota rodoviária não está habilitado.");
        String payload = "{\"coordinates\":[[" + number(origin.longitude()) + "," + number(origin.latitude()) + "],[" + number(destination.longitude()) + "," + number(destination.latitude()) + "]],\"instructions\":false,\"geometry\":false}";
        Response response = send(payload);
        if (response.statusCode() == 404 || response.statusCode() == 400)
            throw OrderService.error(HttpStatus.UNPROCESSABLE_ENTITY, "ROUTE_NOT_FOUND", "Não foi possível encontrar uma rota para os endereços confirmados.");
        if (response.statusCode() == 401 || response.statusCode() == 403 || response.statusCode() == 429)
            throw OrderService.error(HttpStatus.SERVICE_UNAVAILABLE, response.statusCode() == 429 ? "ROUTE_RATE_LIMITED" : "INTEGRATION_UNAVAILABLE", "O provedor de rota não está disponível para esta consulta.");
        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw OrderService.error(HttpStatus.SERVICE_UNAVAILABLE, "INTEGRATION_UNAVAILABLE", "O provedor de rota não respondeu normalmente.");
        try {
            JsonNode root = json.readTree(response.body());
            JsonNode summary = root.path("routes").path(0).path("summary");
            if (!summary.isObject()) throw invalidRoute();
            BigDecimal distance = decimal(summary.get("distance"));
            BigDecimal duration = decimal(summary.get("duration"));
            if (distance == null || duration == null || distance.signum() <= 0 || duration.signum() <= 0
                    || distance.compareTo(BigDecimal.valueOf(MAX_COORDINATE_DISTANCE_METERS)) > 0
                    || distance.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0
                    || duration.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0) throw invalidRoute();
            return new RouteResult("openrouteservice", "ors:" + digest(response.body()), distance.setScale(0, java.math.RoundingMode.HALF_UP).intValueExact(), duration.setScale(0, java.math.RoundingMode.HALF_UP).intValueExact(), false, Instant.now());
        } catch (OrderService.OrderException e) {
            throw e;
        } catch (Exception e) {
            throw invalidRoute();
        }
    }

    private Response send(String payload) {
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(timeout)
                .header("Authorization", apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", "ExamePerto/route-adapter (+local-development)")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8)).build();
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                String body;
                try (InputStream input = response.body()) {
                    byte[] bytes = input.readNBytes(maxResponseBytes + 1);
                    if (bytes.length > maxResponseBytes)
                        throw OrderService.error(HttpStatus.SERVICE_UNAVAILABLE, "INTEGRATION_UNAVAILABLE", "A resposta do provedor de rota excedeu o limite permitido.");
                    body = new String(bytes, StandardCharsets.UTF_8);
                }
                if (attempt < maxRetries && (response.statusCode() == 429 || response.statusCode() == 502 || response.statusCode() == 503 || response.statusCode() == 504)) continue;
                return new Response(response.statusCode(), body);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw OrderService.error(HttpStatus.SERVICE_UNAVAILABLE, "INTEGRATION_UNAVAILABLE", "A consulta de rota foi interrompida.");
            } catch (IOException | RuntimeException e) {
                if (e instanceof OrderService.OrderException orderException) throw orderException;
                if (attempt == maxRetries) throw OrderService.error(HttpStatus.SERVICE_UNAVAILABLE, "INTEGRATION_UNAVAILABLE", "O provedor de rota não respondeu no prazo.");
            }
        }
        throw OrderService.error(HttpStatus.SERVICE_UNAVAILABLE, "INTEGRATION_UNAVAILABLE", "O provedor de rota não respondeu.");
    }

    private static String number(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }
    private static BigDecimal decimal(JsonNode value) { return value == null || !value.isNumber() ? null : value.decimalValue(); }
    private static void validateCoordinate(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null || longitude == null || latitude.abs().compareTo(BigDecimal.valueOf(90)) > 0 || longitude.abs().compareTo(BigDecimal.valueOf(180)) > 0)
            throw OrderService.error(HttpStatus.BAD_REQUEST, "INVALID_ADDRESS", "O endereço precisa de coordenadas confirmadas.");
    }
    private static OrderService.OrderException invalidRoute() { return OrderService.error(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_ROUTE", "A resposta de rota não contém distância/duração válidas."); }
    private static String digest(String body) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    private record Response(int statusCode, String body) {}
}
