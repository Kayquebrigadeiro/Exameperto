package br.com.exameperto.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me/mfa")
class MfaController {
    private final MfaService service; private final String origin;
    MfaController(MfaService service,@Value("${registration.allowed-origin:http://localhost:5173}") String origin){this.service=service;this.origin=origin;}
    @GetMapping MfaStatus status(Authentication a){return service.status(principal(a));}
    @PostMapping("/totp/enrollment") MfaEnrollment enroll(Authentication a,HttpServletRequest r,@Valid @RequestBody MfaPasswordInput in){mutation(principal(a),r);return service.enroll(principal(a),in.password());}
    @PostMapping("/totp/confirmation") MfaStatus confirm(Authentication a,HttpServletRequest r,@Valid @RequestBody MfaCodeInput in){mutation(principal(a),r);return service.confirm(principal(a),in.code());}
    @PostMapping("/verification") MfaStatus verify(Authentication a,HttpServletRequest r,@Valid @RequestBody MfaCodeInput in){mutation(principal(a),r);return service.verify(principal(a),in.code());}
    private AuthService.SessionPrincipal principal(Authentication a){return (AuthService.SessionPrincipal)a.getDetails();}
    private void mutation(AuthService.SessionPrincipal p,HttpServletRequest r){if("WEB".equals(p.client())&&!origin.equals(r.getHeader("Origin")))throw DelivererService.error(HttpStatus.FORBIDDEN,"FORBIDDEN","Origem inválida.");}
}
