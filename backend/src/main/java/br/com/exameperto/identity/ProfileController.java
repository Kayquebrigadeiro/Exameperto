package br.com.exameperto.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me/profile")
class ProfileController {
    private final ProfileService service;
    private final String origin;

    ProfileController(ProfileService service, @Value("${registration.allowed-origin:http://localhost:5173}") String origin) {
        this.service = service;
        this.origin = origin;
    }

    @GetMapping
    ResponseEntity<ProfileView> own(Authentication authentication) {
        ProfileView profile = service.own(principal(authentication).userId());
        return response(HttpStatus.OK, profile);
    }

    @PutMapping
    ResponseEntity<ProfileView> update(Authentication authentication, HttpServletRequest request,
                                       @RequestHeader(value = "If-Match", required = false) String ifMatch,
                                       @Valid @RequestBody ProfileInput input) {
        if ("WEB".equals(principal(authentication).client()) && !origin.equals(request.getHeader("Origin")))
            throw new ProfileService.ProfileException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Origem inválida.");
        long version = version(ifMatch);
        ProfileView profile = service.update(principal(authentication).userId(), input, version);
        return response(HttpStatus.OK, profile);
    }

    @GetMapping("/{ignoredId}")
    ResponseEntity<ProfileView> otherProfileIsNotExposed() {
        throw new ProfileService.ProfileException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Perfil não encontrado.");
    }

    private AuthService.SessionPrincipal principal(Authentication authentication) { return (AuthService.SessionPrincipal) authentication.getDetails(); }
    private long version(String value) {
        if (value == null || !value.matches("\"[0-9]+\"")) throw new ProfileService.ProfileException(HttpStatus.PRECONDITION_REQUIRED, "PRECONDITION_REQUIRED", "Informe If-Match.");
        try { return Long.parseLong(value.substring(1, value.length() - 1)); }
        catch (NumberFormatException ex) { throw new ProfileService.ProfileException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "If-Match inválido."); }
    }
    private ResponseEntity<ProfileView> response(HttpStatus status, ProfileView profile) {
        return ResponseEntity.status(status).header(HttpHeaders.ETAG, "\"" + profile.version() + "\"").header(HttpHeaders.CACHE_CONTROL, "no-store").body(profile);
    }
}
