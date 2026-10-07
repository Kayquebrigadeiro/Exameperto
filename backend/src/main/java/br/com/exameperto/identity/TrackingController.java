package br.com.exameperto.identity;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/orders/{orderId}/locations")
class TrackingController {
    private final TrackingService service; private final long maxPayloadBytes;
    TrackingController(TrackingService service,@Value("${tracking.max-payload-bytes:1024}")long maxPayloadBytes){this.service=service;this.maxPayloadBytes=maxPayloadBytes;}
    @PostMapping ResponseEntity<LocationView> send(Authentication authentication,@PathVariable UUID orderId,HttpServletRequest request,@Valid @RequestBody LocationInput input){if(request.getContentLengthLong()>maxPayloadBytes)throw TrackingAccessService.error(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE,"LOCATION_PAYLOAD_TOO_LARGE","Payload de localização excede o limite permitido.");TrackingService.AcceptedLocation result=service.accept(user(authentication),orderId,input);return ResponseEntity.status(result.created()?201:200).body(result.location());}
    @GetMapping("/latest") LocationView latest(Authentication authentication,@PathVariable UUID orderId){return service.latest(user(authentication),orderId);}
    private UUID user(Authentication a){return ((AuthService.SessionPrincipal)a.getDetails()).userId();}
}
