package br.com.exameperto.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public final class AccessTokenFilter extends OncePerRequestFilter {
    private final AuthService auth;
    public AccessTokenFilter(AuthService auth) { this.auth=auth; }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        String header=request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) auth.authenticateAccess(header.substring(7)).ifPresent(p -> {
            var token=new UsernamePasswordAuthenticationToken(p.userId(),null,java.util.List.of()); token.setDetails(p); SecurityContextHolder.getContext().setAuthentication(token);
        });
        chain.doFilter(request,response);
    }
}
