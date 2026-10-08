package br.com.exameperto.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
final class RecoveryAccessFilter extends OncePerRequestFilter {
    private final RecoveryGuard recovery;
    RecoveryAccessFilter(RecoveryGuard recovery) { this.recovery=recovery; }

    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
        throws ServletException,IOException {
        if (recovery.blocked()) {
            response.setStatus(503);
            response.setContentType("application/json");
            response.setHeader("Cache-Control","no-store");
            response.getWriter().write("{\"code\":\"RESTORE_BLOCKED\",\"message\":\"Restauração ainda não verificada.\"}");
            return;
        }
        chain.doFilter(request,response);
    }
}
