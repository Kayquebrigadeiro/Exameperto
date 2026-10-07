package br.com.exameperto.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
class AssignmentController {
    private final AssignmentService service;private final String origin;
    AssignmentController(AssignmentService service,@Value("${registration.allowed-origin:http://localhost:5173}")String origin){this.service=service;this.origin=origin;}
    @GetMapping("/offers") OfferPageView offers(Authentication a){return new OfferPageView(service.offers(user(a)),null);}
    @PostMapping("/orders/{id}/assignment") ResponseEntity<AssignmentView> accept(Authentication a,HttpServletRequest r,@PathVariable UUID id,@RequestHeader("If-Match")String etag,@RequestHeader("Idempotency-Key")String key,@Valid @RequestBody AcceptTaskInput input){mutation(a,r);return ResponseEntity.status(HttpStatus.CREATED).body(service.accept(user(a),id,version(etag),key,input));}
    @GetMapping("/orders/{id}/assignment") AssignmentView assignment(Authentication a,@PathVariable UUID id){return service.get(user(a),id);}
    @GetMapping("/orders/{id}/driver") OperationalIdentityView identity(Authentication a,@PathVariable UUID id){return service.identity(user(a),id);}
    @GetMapping("/me/assignments") List<AssignmentView> own(Authentication a){return service.own(user(a));}
    private UUID user(Authentication a){return ((AuthService.SessionPrincipal)a.getDetails()).userId();}
    private long version(String value){try{return Long.parseLong(value.replace("\"",""));}catch(Exception ex){throw OrderService.error(HttpStatus.PRECONDITION_REQUIRED,"IF_MATCH_REQUIRED","If-Match deve conter a versão da oferta.");}}
    private void mutation(Authentication a,HttpServletRequest r){if("WEB".equals(((AuthService.SessionPrincipal)a.getDetails()).client())&&!origin.equals(r.getHeader("Origin")))throw OrderService.error(HttpStatus.FORBIDDEN,"FORBIDDEN","Origem inválida.");}
}
