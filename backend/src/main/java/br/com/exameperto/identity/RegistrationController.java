package br.com.exameperto.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class RegistrationController {
    private final RegistrationService service;
    RegistrationController(RegistrationService service) { this.service = service; }

    @PostMapping("/register")
    ResponseEntity<Void> register(@Valid @RequestBody RegistrationRequest request, HttpServletRequest http) {
        service.register(request, clientKey(http));
        return ResponseEntity.accepted().build();
    }

    private String clientKey(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
