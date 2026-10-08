package br.com.exameperto;

import br.com.exameperto.identity.AccessTokenFilter;
import br.com.exameperto.identity.AuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean SecurityFilterChain security(HttpSecurity http, AuthService auth, @Value("${registration.attempts-per-minute:5}") int limit, @Value("${registration.global-attempts-per-minute:100}") int globalLimit) throws Exception {
        http.csrf(csrf -> csrf.disable()).cors(Customizer.withDefaults()).sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(new br.com.exameperto.identity.AuthRateFilter(limit, globalLimit), UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(e -> e.authenticationEntryPoint((req,res,ex) -> { res.setStatus(401); res.setContentType("application/json"); res.getWriter().write("{\"code\":\"UNAUTHENTICATED\",\"message\":\"Sessão inválida.\",\"correlationId\":\""+java.util.UUID.randomUUID()+"\",\"retryable\":false}"); }))
            .addFilterBefore(new AccessTokenFilter(auth), UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(a -> a.requestMatchers("/ws", "/ws/**").permitAll().requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf").permitAll().requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/verification", "/api/v1/auth/verification/resend", "/api/v1/auth/recovery", "/api/v1/auth/recovery/complete", "/api/v1/auth/refresh", "/api/v1/auth/csrf", "/api/v1/integrations/payments/events").permitAll().requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").authenticated().requestMatchers("/api/v1/me/**", "/api/v1/patients/**", "/api/v1/family-invitations/**", "/api/v1/documents/**", "/api/v1/benefit-requests/**", "/api/v1/analyst/**", "/api/v1/privacy-requests/**", "/api/v1/programs/**", "/api/v1/funding/**", "/api/v1/orders/**", "/api/v1/order-documents/**", "/api/v1/quotes/**", "/api/v1/offers/**", "/api/v1/assignments/**", "/api/v1/financial/**", "/api/v1/payouts/**").authenticated().anyRequest().denyAll());
        return http.build();
    }
    @Bean CorsConfigurationSource cors(@Value("${registration.allowed-origin:http://localhost:5173}") String origin) {
        CorsConfiguration config = new CorsConfiguration(); config.setAllowedOrigins(java.util.List.of(origin)); config.setAllowedMethods(java.util.List.of("POST","GET","PUT","DELETE")); config.setAllowedHeaders(java.util.List.of("Content-Type","Authorization","X-CSRF-Token","X-Requested-With","Origin","If-Match","Idempotency-Key")); config.setExposedHeaders(java.util.List.of("ETag")); config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource(); source.registerCorsConfiguration("/api/**", config); return source;
    }
}
