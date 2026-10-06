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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
class BenefitController {
    private final BenefitService service; private final String origin;
    BenefitController(BenefitService service,@Value("${registration.allowed-origin:http://localhost:5173}") String origin){this.service=service;this.origin=origin;}
    @PostMapping("/benefit-requests") ResponseEntity<BenefitRequestView> create(Authentication a,HttpServletRequest r,@Valid @RequestBody BenefitRequestInput input){mutation(a,r);return ResponseEntity.status(HttpStatus.CREATED).body(service.create(user(a),input));}
    @GetMapping("/benefit-requests") List<BenefitRequestView> list(Authentication a){return service.list(user(a));}
    @GetMapping("/benefit-requests/{id}") BenefitRequestView get(Authentication a,@PathVariable UUID id){return service.get(user(a),id);}
    @PostMapping(value="/benefit-requests/{id}/documents",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) BenefitRequestView upload(Authentication a,HttpServletRequest r,@PathVariable UUID id,@RequestParam long version,@RequestParam BenefitDimension purpose,@RequestParam MultipartFile file){mutation(a,r);return service.upload(user(a),id,version,purpose,file);}
    @PostMapping("/benefit-requests/{id}/documents/reuse") BenefitRequestView reuse(Authentication a,HttpServletRequest r,@PathVariable UUID id,@Valid @RequestBody BenefitDocumentReuseInput input){mutation(a,r);return service.reuse(user(a),id,input);}
    @PostMapping("/benefit-requests/{id}/appeal") BenefitRequestView appeal(Authentication a,HttpServletRequest r,@PathVariable UUID id,@Valid @RequestBody BenefitAppealInput input){mutation(a,r);return service.appeal(user(a),id,input);}
    @GetMapping("/analyst/benefit-reviews") List<BenefitReviewView> reviews(Authentication a){return service.queue(user(a));}
    @PostMapping("/analyst/benefit-reviews/{id}/assign") BenefitReviewView assign(Authentication a,HttpServletRequest r,@PathVariable UUID id){mutation(a,r);return service.assign(principal(a),id);}
    @PostMapping("/analyst/benefit-reviews/{id}/documents/{documentId}/inspection") BenefitReviewView inspect(Authentication a,HttpServletRequest r,@PathVariable UUID id,@PathVariable UUID documentId){mutation(a,r);return service.inspect(principal(a),id,documentId);}
    @PostMapping("/analyst/benefit-reviews/{id}/decision") BenefitReviewView decision(Authentication a,HttpServletRequest r,@PathVariable UUID id,@Valid @RequestBody BenefitDecisionInput input){mutation(a,r);return service.decide(principal(a),id,input);}
    private UUID user(Authentication a){return principal(a).userId();}
    private AuthService.SessionPrincipal principal(Authentication a){return (AuthService.SessionPrincipal)a.getDetails();}
    private void mutation(Authentication a,HttpServletRequest r){if("WEB".equals(principal(a).client())&&!origin.equals(r.getHeader("Origin")))throw DelivererService.error(HttpStatus.FORBIDDEN,"FORBIDDEN","Origem inválida.");}
}
