package br.com.exameperto.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Applies endpoint-specific limits before MVC attempts to deserialize a request body. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
final class RequestBodyLimitFilter extends OncePerRequestFilter {
    private static final int MAX_CONFIGURABLE_BYTES = 1024 * 1024;
    private final int paymentEventBytes;
    private final int locationBytes;

    RequestBodyLimitFilter(
        @Value("${payment.max-event-payload-bytes:65536}") int paymentEventBytes,
        @Value("${tracking.max-payload-bytes:1024}") int locationBytes) {
        this.paymentEventBytes = checked(paymentEventBytes, "payment.max-event-payload-bytes");
        this.locationBytes = checked(locationBytes, "tracking.max-payload-bytes");
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
        FilterChain chain) throws ServletException, IOException {
        Limit limit = limitFor(request);
        if (limit == null) {
            chain.doFilter(request, response);
            return;
        }
        long declared = request.getContentLengthLong();
        if (declared > limit.bytes()) {
            reject(response, limit);
            return;
        }
        byte[] body = request.getInputStream().readNBytes(limit.bytes() + 1);
        if (body.length > limit.bytes()) {
            reject(response, limit);
            return;
        }
        chain.doFilter(new BodyRequest(request, body), response);
    }

    private Limit limitFor(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod())) return null;
        String uri = request.getRequestURI();
        if ("/api/v1/integrations/payments/events".equals(uri))
            return new Limit(paymentEventBytes, "PAYMENT_EVENT_PAYLOAD_TOO_LARGE", "Evento de pagamento excede o limite permitido.");
        String prefix = "/api/v1/orders/", suffix = "/locations";
        if (uri.startsWith(prefix) && uri.endsWith(suffix)
            && !uri.substring(prefix.length(), uri.length() - suffix.length()).contains("/"))
            return new Limit(locationBytes, "LOCATION_PAYLOAD_TOO_LARGE", "Payload de localização excede o limite permitido.");
        return null;
    }

    private static void reject(HttpServletResponse response, Limit limit) throws IOException {
        response.setStatus(413);
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write("{\"code\":\"" + limit.code() + "\",\"message\":\"" + limit.message()
            + "\",\"correlationId\":\"" + java.util.UUID.randomUUID() + "\",\"retryable\":false}");
    }

    private static int checked(int value, String property) {
        if (value < 1 || value > MAX_CONFIGURABLE_BYTES)
            throw new IllegalArgumentException(property + " deve estar entre 1 e 1048576 bytes.");
        return value;
    }

    private record Limit(int bytes, String code, String message) {}

    private static final class BodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;
        BodyRequest(HttpServletRequest request, byte[] body) { super(request); this.body = body; }
        @Override public int getContentLength() { return body.length; }
        @Override public long getContentLengthLong() { return body.length; }
        @Override public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public int read() { return input.read(); }
                @Override public int read(byte[] bytes, int offset, int length) { return input.read(bytes, offset, length); }
                @Override public boolean isFinished() { return input.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) {
                    try { listener.onDataAvailable(); if (isFinished()) listener.onAllDataRead(); }
                    catch (IOException exception) { listener.onError(exception); }
                }
            };
        }
        @Override public BufferedReader getReader() {
            String encoding = getCharacterEncoding();
            Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }
}
