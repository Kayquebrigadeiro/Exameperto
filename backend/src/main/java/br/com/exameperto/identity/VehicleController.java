package br.com.exameperto.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
class VehicleController {
    private final VehicleService service; private final DelivererService evidence; private final String origin;
    VehicleController(VehicleService service,DelivererService evidence,@Value("${registration.allowed-origin:http://localhost:5173}") String origin){this.service=service;this.evidence=evidence;this.origin=origin;}

    @PostMapping("/me/vehicle-links") ResponseEntity<VehicleLinkView> create(Authentication a,HttpServletRequest r,@Valid @RequestBody VehicleInput input){mutation(a,r);return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.CACHE_CONTROL,"no-store").body(service.create(user(a),input));}
    @GetMapping("/me/vehicle-links") List<VehicleLinkView> list(Authentication a){return service.own(user(a));}
    @GetMapping("/me/vehicle-links/{id}") VehicleLinkView get(Authentication a,@PathVariable UUID id){return service.link(user(a),id);}
    @PutMapping("/me/vehicle-links/{id}") VehicleLinkView update(Authentication a,HttpServletRequest r,@PathVariable UUID id,@RequestParam long version,@Valid @RequestBody VehicleInput input){mutation(a,r);return service.update(user(a),id,version,input);}
    @PostMapping(value="/me/vehicle-links/{id}/documents",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) VehicleLinkView upload(Authentication a,HttpServletRequest r,@PathVariable UUID id,@RequestParam long version,@RequestParam ReuseDocumentInput.Purpose purpose,@RequestParam MultipartFile file){mutation(a,r);return service.upload(user(a),id,version,purpose,file);}
    @PostMapping("/me/vehicle-links/{id}/documents/reuse") VehicleLinkView reuse(Authentication a,HttpServletRequest r,@PathVariable UUID id,@Valid @RequestBody ReuseDocumentInput input){mutation(a,r);return service.reuse(user(a),id,input);}

    @GetMapping("/analyst/vehicle-reviews") List<VehicleReviewView> reviews(Authentication a){return service.queue(user(a));}
    @PostMapping("/analyst/vehicle-reviews/{id}/assign") VehicleReviewView assign(Authentication a,HttpServletRequest r,@PathVariable UUID id){mutation(a,r);return service.assign(principal(a),id);}
    @PostMapping("/analyst/vehicle-reviews/{id}/documents/{documentId}/inspection") VehicleReviewView inspect(Authentication a,HttpServletRequest r,@PathVariable UUID id,@PathVariable UUID documentId){mutation(a,r);return service.inspect(principal(a),id,documentId);}
    @GetMapping("/analyst/vehicle-reviews/{id}/documents/{documentId}/download") ResponseEntity<InputStreamResource> download(Authentication a,@PathVariable UUID id,@PathVariable UUID documentId){DelivererService.Download d=service.download(principal(a),id,documentId);InputStream in=evidence.open(d.key());return ResponseEntity.ok().contentType(MediaType.parseMediaType(d.mime())).contentLength(d.size()).header(HttpHeaders.CACHE_CONTROL,"no-store").header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=arquivo-"+documentId).body(new InputStreamResource(in));}
    @PostMapping("/analyst/vehicle-reviews/{id}/decision") VehicleReviewView decision(Authentication a,HttpServletRequest r,@PathVariable UUID id,@RequestBody Map<String,String> body){mutation(a,r);return service.decide(principal(a),id,body.get("decision"),body.get("reason"));}

    private UUID user(Authentication a){return principal(a).userId();}
    private AuthService.SessionPrincipal principal(Authentication a){return (AuthService.SessionPrincipal)a.getDetails();}
    private void mutation(Authentication a,HttpServletRequest r){if("WEB".equals(principal(a).client())&&!origin.equals(r.getHeader("Origin")))throw DelivererService.error(HttpStatus.FORBIDDEN,"FORBIDDEN","Origem inválida.");}
}
