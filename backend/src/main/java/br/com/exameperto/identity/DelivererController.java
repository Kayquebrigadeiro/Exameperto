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
class DelivererController {
    private final DelivererService service; private final String origin;
    DelivererController(DelivererService service,@Value("${registration.allowed-origin:http://localhost:5173}") String origin){this.service=service;this.origin=origin;}
    @PostMapping("/me/deliverer") ResponseEntity<DelivererView> create(Authentication a,HttpServletRequest r,@Valid @RequestBody DelivererInput input){mutation(a,r);return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.CACHE_CONTROL,"no-store").body(service.createOrUpdate(user(a),input,null));}
    @PutMapping("/me/deliverer") ResponseEntity<DelivererView> update(Authentication a,HttpServletRequest r,@RequestParam("version") long version,@Valid @RequestBody DelivererInput input){mutation(a,r);return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"no-store").body(service.createOrUpdate(user(a),input,version));}
    @GetMapping("/me/deliverer") DelivererView own(Authentication a){return service.own(user(a));}
    @PostMapping(value="/me/deliverer/documents",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) ResponseEntity<DocumentView> upload(Authentication a,HttpServletRequest r,@RequestParam String category,@RequestParam MultipartFile file){mutation(a,r);return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.CACHE_CONTROL,"no-store").body(service.upload(user(a),category,file));}
    @GetMapping("/me/deliverer/documents") List<DocumentView> documents(Authentication a){return service.documents(user(a));}
    @GetMapping("/documents/{id}/download") ResponseEntity<InputStreamResource> download(Authentication a,@PathVariable UUID id){DelivererService.Download d=service.authorizedDownload(user(a),id);InputStream in=service.open(d.key());return ResponseEntity.ok().contentType(MediaType.parseMediaType(d.mime())).contentLength(d.size()).header(HttpHeaders.CACHE_CONTROL,"no-store").header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=arquivo-"+id).body(new InputStreamResource(in));}
    @GetMapping("/analyst/reviews") List<ReviewView> reviews(Authentication a){return service.queue(user(a));}
    @PostMapping("/analyst/reviews/{id}/assign") ReviewView assign(Authentication a,HttpServletRequest r,@PathVariable UUID id){mutation(a,r);return service.assign(principal(a),id);}
    @PostMapping("/analyst/reviews/{id}/inspection") ReviewView inspect(Authentication a,HttpServletRequest r,@PathVariable UUID id){mutation(a,r);return service.inspect(principal(a),id);}
    @PostMapping("/analyst/reviews/{id}/decision") ReviewView decision(Authentication a,HttpServletRequest r,@PathVariable UUID id,@RequestBody Map<String,String> body){mutation(a,r);return service.decide(principal(a),id,body.get("decision"),body.get("reason"));}
    private UUID user(Authentication a){return ((AuthService.SessionPrincipal)a.getDetails()).userId();}
    private AuthService.SessionPrincipal principal(Authentication a){return (AuthService.SessionPrincipal)a.getDetails();}
    private void mutation(Authentication a,HttpServletRequest r){if("WEB".equals(((AuthService.SessionPrincipal)a.getDetails()).client())&&!origin.equals(r.getHeader("Origin")))throw DelivererService.error(HttpStatus.FORBIDDEN,"FORBIDDEN","Origem inválida.");}
}
