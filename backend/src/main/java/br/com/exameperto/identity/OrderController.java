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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
class OrderController {
    private final OrderService service; private final String origin;
    OrderController(OrderService service,@Value("${registration.allowed-origin:http://localhost:5173}") String origin){this.service=service;this.origin=origin;}
    @PostMapping("/orders") ResponseEntity<OrderView> create(Authentication a,HttpServletRequest r,@RequestHeader(value="Idempotency-Key",required=false) String key,@Valid @RequestBody OrderInput input){mutation(a,r);return ResponseEntity.status(HttpStatus.CREATED).body(service.create(user(a),input,key));}
    @GetMapping("/orders") List<OrderView> list(Authentication a){return service.list(user(a));}
    @PostMapping(value="/order-documents/pickup-authorization",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) ResponseEntity<DocumentView> uploadAuthorization(Authentication a,HttpServletRequest r,@RequestParam MultipartFile file){mutation(a,r);return ResponseEntity.status(HttpStatus.CREATED).body(service.uploadAuthorizationDocument(user(a),file));}
    @GetMapping("/orders/{id}") OrderView get(Authentication a,@PathVariable UUID id){return service.get(user(a),id);}
    @GetMapping("/orders/{id}/addresses") OrderAddressesView addresses(Authentication a,@PathVariable UUID id){return service.addresses(user(a),id);}
    @PutMapping("/orders/{id}/addresses") OrderView updateAddresses(Authentication a,HttpServletRequest r,@PathVariable UUID id,@Valid @RequestBody OrderAddressUpdateInput input){mutation(a,r);return service.updateAddresses(user(a),id,input);}
    @PostMapping(value="/orders/{id}/pickup-authorization",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) ResponseEntity<OrderView> authorization(Authentication a,HttpServletRequest r,@PathVariable UUID id,@RequestParam MultipartFile file){mutation(a,r);return ResponseEntity.status(HttpStatus.CREATED).body(service.attachAuthorization(user(a),id,file));}
    @PostMapping("/orders/{id}/quotes") ResponseEntity<QuoteView> quote(Authentication a,HttpServletRequest r,@PathVariable UUID id,@RequestHeader("If-Match") String etag,@RequestHeader(value="Idempotency-Key",required=false) String key,@Valid @RequestBody QuoteInput input){mutation(a,r);return ResponseEntity.status(HttpStatus.CREATED).body(service.quote(user(a),id,version(etag),key,input));}
    @GetMapping("/orders/{id}/quotes") List<QuoteView> quotes(Authentication a,@PathVariable UUID id){return service.quotes(user(a),id);}
    @GetMapping("/quotes/{id}") QuoteView quote(Authentication a,@PathVariable UUID id){return service.getQuote(user(a),id);}
    @PostMapping("/quotes/{id}/accept") ResponseEntity<OrderView> accept(Authentication a,HttpServletRequest r,@PathVariable UUID id,@RequestHeader("If-Match") String etag,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody AcceptQuoteInput input){mutation(a,r);return ResponseEntity.accepted().body(service.accept(user(a),id,version(etag),key,input));}
    @PostMapping("/orders/{id}/cancel") OrderView cancel(Authentication a,HttpServletRequest r,@PathVariable UUID id,@RequestHeader("If-Match")String etag,@RequestHeader("Idempotency-Key")String key,@Valid @RequestBody CancelOrderInput input){mutation(a,r);return service.cancel(user(a),id,version(etag),key,input);}
    private UUID user(Authentication a){return ((AuthService.SessionPrincipal)a.getDetails()).userId();}
    private long version(String value){try{return Long.parseLong(value.replace("\"",""));}catch(Exception ex){throw OrderService.error(HttpStatus.PRECONDITION_REQUIRED,"IF_MATCH_REQUIRED","If-Match deve conter a versão do pedido.");}}
    private void mutation(Authentication a,HttpServletRequest r){AuthService.SessionPrincipal p=(AuthService.SessionPrincipal)a.getDetails();if("WEB".equals(p.client())&&!origin.equals(r.getHeader("Origin")))throw OrderService.error(HttpStatus.FORBIDDEN,"FORBIDDEN","Origem inválida.");}
}
