package br.com.exameperto.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
class FundingController {
    private final FundingService service; private final String origin;
    FundingController(FundingService service,@Value("${registration.allowed-origin:http://localhost:5173}") String origin){this.service=service;this.origin=origin;}
    @PostMapping(value="/me/financial-documents",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<DocumentView> uploadEvidence(Authentication a,HttpServletRequest r,@RequestParam MultipartFile file){mutation(a,r);return ResponseEntity.status(HttpStatus.CREATED).body(service.uploadEvidence(user(a),file));}
    @GetMapping("/programs/{programId}/balance") BalanceView balance(Authentication a,@PathVariable UUID programId){return service.balance(principal(a),programId);}
    @PostMapping("/programs/{programId}/funding") ResponseEntity<FundingView> record(Authentication a,HttpServletRequest r,@PathVariable UUID programId,@RequestHeader(value="Idempotency-Key",required=false) String idem,@Valid @RequestBody FundingInput input){mutation(a,r);return ResponseEntity.status(HttpStatus.CREATED).body(service.record(principal(a),programId,input,idem));}
    @GetMapping("/programs/{programId}/funding") List<FundingView> list(Authentication a,@PathVariable UUID programId){return service.list(principal(a),programId);}
    @PostMapping("/funding/{fundingId}/review") FundingView review(Authentication a,HttpServletRequest r,@PathVariable UUID fundingId,@RequestHeader(value="If-Match",required=false) String etag,@RequestHeader(value="Idempotency-Key",required=false) String idem,@Valid @RequestBody FundingReviewInput input){mutation(a,r);return service.review(principal(a),fundingId,input,version(etag),idem);}
    private long version(String value){try{return Long.parseLong(value.replace("\"",""));}catch(Exception ex){throw FundingService.error(HttpStatus.PRECONDITION_REQUIRED,"IF_MATCH_REQUIRED","If-Match deve conter a versão do aporte.");}}
    private UUID user(Authentication a){return principal(a).userId();}
    private AuthService.SessionPrincipal principal(Authentication a){return (AuthService.SessionPrincipal)a.getDetails();}
    private void mutation(Authentication a,HttpServletRequest r){if("WEB".equals(principal(a).client())&&!origin.equals(r.getHeader("Origin")))throw DelivererService.error(HttpStatus.FORBIDDEN,"FORBIDDEN","Origem inválida.");}
}
