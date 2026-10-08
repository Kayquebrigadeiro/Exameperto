package br.com.exameperto.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
class PrivacyController {
    private final PrivacyService service;
    private final String origin;
    PrivacyController(PrivacyService service,@Value("${registration.allowed-origin:http://localhost:5173}") String origin) { this.service=service; this.origin=origin; }

    @PostMapping("/me/privacy-requests")
    ResponseEntity<PrivacyRequestView> create(Authentication auth,HttpServletRequest request,@Valid @RequestBody PrivacyRequestInput input,@RequestHeader(value="Idempotency-Key",required=false) String key) {
        mutation(auth,request); return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.create(user(auth),input,key));
    }
    @GetMapping("/me/privacy-requests/{id}") PrivacyRequestView get(Authentication auth,@PathVariable UUID id) { return service.get(user(auth),id); }
    @PostMapping("/privacy-requests/{id}/response") PrivacyRequestView response(Authentication auth,HttpServletRequest request,@PathVariable UUID id,@Valid @RequestBody PrivacyResponseInput input) { mutation(auth,request); return service.respond(principal(auth),id,input); }
    @PostMapping("/privacy-requests/{id}/purge") PrivacyRequestView authorize(Authentication auth,HttpServletRequest request,@PathVariable UUID id) { mutation(auth,request); return service.authorizePurge(principal(auth),id); }
    @PostMapping("/privacy-requests/{id}/purge/execute") PrivacyRequestView execute(Authentication auth,HttpServletRequest request,@PathVariable UUID id) { mutation(auth,request); return service.execute(principal(auth),id); }
    @PostMapping("/privacy-requests/{id}/purge/verify") PrivacyRequestView verify(Authentication auth,HttpServletRequest request,@PathVariable UUID id) { mutation(auth,request); return service.verify(principal(auth),id); }

    private UUID user(Authentication auth) { return principal(auth).userId(); }
    private AuthService.SessionPrincipal principal(Authentication auth) { return (AuthService.SessionPrincipal)auth.getDetails(); }
    private void mutation(Authentication auth,HttpServletRequest request) {
        if ("WEB".equals(principal(auth).client())&&!origin.equals(request.getHeader("Origin")))
            throw PrivacyService.error(HttpStatus.FORBIDDEN,"FORBIDDEN","Origem inválida.");
    }
}
