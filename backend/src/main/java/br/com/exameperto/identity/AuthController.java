package br.com.exameperto.identity;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {
    private final AuthService auth;
    private final AccountMailService mail;
    private final DataProtector protector;
    private final String origin;
    AuthController(AuthService auth, AccountMailService mail, DataProtector protector,
                   @Value("${registration.allowed-origin}") String origin) {
        this.auth=auth; this.mail=mail; this.protector=protector; this.origin=origin;
    }
    @PostMapping("/verification") AcceptedResponse verify(@Valid @RequestBody TokenInput input) {
        auth.verify(input.token()); return accepted("CONCLUIDA");
    }
    @PostMapping("/verification/resend") ResponseEntity<AcceptedResponse> resend(@Valid @RequestBody EmailInput input) {
        mail.request(input.email(),"EMAIL"); return ResponseEntity.accepted().body(accepted("PENDENTE"));
    }
    @PostMapping("/login") TokenResponse login(@Valid @RequestBody LoginRequest input, HttpServletRequest request, HttpServletResponse response) {
        if (input.client()==LoginRequest.Client.WEB) requireOrigin(request);
        else if (cookie(request)!=null || request.getHeader("Origin")!=null) throw forbidden();
        return withCookie(auth.login(input), input.client().name(), response);
    }
    @PostMapping("/refresh") TokenResponse refresh(@Valid @RequestBody RefreshBody body, HttpServletRequest request, HttpServletResponse response) {
        String cookie=cookie(request); boolean web=cookie!=null;
        if (web) { requireCsrf(request); if (body.refreshToken()!=null) throw forbidden(); }
        else if (request.getHeader("Origin")!=null) throw forbidden();
        return withCookie(auth.refresh(web?cookie:body.refreshToken(),web?"WEB":"MOBILE"),web?"WEB":"MOBILE",response);
    }
    @PostMapping("/logout") ResponseEntity<Void> logout(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        var principal=(AuthService.SessionPrincipal)authentication.getDetails();
        if (principal.client().equals("WEB")) requireCsrf(request);
        auth.logout(principal);
        if (principal.client().equals("WEB")) response.addHeader(HttpHeaders.SET_COOKIE,refreshCookie("").maxAge(0).build().toString());
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/recovery") ResponseEntity<AcceptedResponse> recovery(@Valid @RequestBody EmailInput input) {
        mail.request(input.email(),"RECUPERACAO"); return ResponseEntity.accepted().body(accepted("PENDENTE"));
    }
    @PostMapping("/recovery/complete") AcceptedResponse complete(@Valid @RequestBody ResetPasswordRequest input) {
        auth.completeRecovery(input.token(),input.newPassword()); return accepted("CONCLUIDA");
    }
    @RequestMapping(value="/csrf", method={RequestMethod.GET,RequestMethod.POST}) Map<String,String> csrf(HttpServletRequest request) {
        requireOrigin(request);
        if (!protector.configured()) throw AuthService.unavailable();
        String refresh=cookie(request);
        if (refresh==null) throw forbidden();
        return Map.of("token",csrfToken(refresh));
    }
    private TokenResponse withCookie(TokenResponse out, String client, HttpServletResponse response) {
        if (client.equals("WEB")) {
            response.addHeader(HttpHeaders.SET_COOKIE,refreshCookie(out.refreshToken()).build().toString());
            return new TokenResponse(out.accessToken(),out.tokenType(),out.expiresIn(),null);
        }
        return out;
    }
    private ResponseCookie.ResponseCookieBuilder refreshCookie(String token) {
        return ResponseCookie.from("refresh",token).httpOnly(true).secure(true).sameSite("Lax").path("/api/v1/auth");
    }
    private String cookie(HttpServletRequest request) {
        if (request.getCookies()!=null) for (Cookie c:request.getCookies()) if ("refresh".equals(c.getName())) return c.getValue();
        return null;
    }
    private String csrfToken(String refresh) { return Base64.getUrlEncoder().withoutPadding().encodeToString(protector.lookup("csrf:"+refresh)); }
    private void requireOrigin(HttpServletRequest request) { if (!origin.equals(request.getHeader("Origin"))) throw forbidden(); }
    private void requireCsrf(HttpServletRequest request) {
        requireOrigin(request); String refresh=cookie(request), header=request.getHeader("X-CSRF-Token");
        if (refresh==null || header==null || !MessageDigest.isEqual(csrfToken(refresh).getBytes(StandardCharsets.UTF_8),header.getBytes(StandardCharsets.UTF_8))) throw forbidden();
    }
    private AuthService.AuthException forbidden() { return new AuthService.AuthException(HttpStatus.FORBIDDEN,"FORBIDDEN","Origem ou proteção CSRF inválida."); }
    private AcceptedResponse accepted(String status) { return new AcceptedResponse(UUID.randomUUID(),status); }
    record RefreshBody(@Size(min=43,max=43) String refreshToken) {}
}
