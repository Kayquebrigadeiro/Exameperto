package br.com.exameperto.identity;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.web.filter.OncePerRequestFilter;

public final class AuthRateFilter extends OncePerRequestFilter {
    private final Cache<String,AtomicInteger> attempts=Caffeine.newBuilder().maximumSize(10000).expireAfterWrite(Duration.ofMinutes(1)).build();
    private final int limit, globalLimit;
    public AuthRateFilter(int limit, int globalLimit) { this.limit=limit; this.globalLimit=globalLimit; }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        response.setHeader("Cache-Control","no-store");
        if (request.getRequestURI().startsWith("/api/v1/auth/") && !request.getMethod().equals("OPTIONS")) {
            if (attempts.get("global", k->new AtomicInteger()).incrementAndGet()>globalLimit ||
                attempts.get(request.getRemoteAddr()+":"+request.getRequestURI(),k->new AtomicInteger()).incrementAndGet()>limit) {
                response.setStatus(429); response.setHeader("Retry-After","60"); response.setContentType("application/json");
                response.getWriter().write("{\"code\":\"RATE_LIMITED\",\"message\":\"Tente novamente mais tarde.\",\"correlationId\":\""+java.util.UUID.randomUUID()+"\",\"retryable\":true}"); return;
            }
        }
        chain.doFilter(request,response);
    }
}
