package br.com.exameperto.identity;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
class PayoutController {
    private final PayoutService service;private final PayoutDispatcher dispatcher;private final String origin;
    PayoutController(PayoutService service,PayoutDispatcher dispatcher,@Value("${registration.allowed-origin:http://localhost:5173}")String origin){this.service=service;this.dispatcher=dispatcher;this.origin=origin;}
    @GetMapping("/financial/payouts") PayoutPage panel(Authentication a){return service.panel(principal(a));}
    @GetMapping("/payouts/{id}") PayoutView get(Authentication a,@PathVariable UUID id){return service.get(principal(a),id);}
    @GetMapping("/orders/{orderId}/payout") PayoutView order(Authentication a,@PathVariable UUID orderId){return service.forOrder(principal(a).userId(),orderId);}
    @GetMapping("/orders/{orderId}/financial-operations") java.util.List<FinancialOperationView> operations(Authentication a,@PathVariable UUID orderId){return service.financialForOrder(principal(a).userId(),orderId);}
    @GetMapping("/financial-operations/{operationId}") FinancialOperationView operation(Authentication a,@PathVariable UUID operationId){return service.financial(principal(a).userId(),operationId);}
    @PostMapping("/payouts/{id}/request") PayoutView request(Authentication a,HttpServletRequest r,@PathVariable UUID id,@RequestHeader(value="Idempotency-Key",required=false)String key){mutation(a,r);PayoutView v=service.request(principal(a),id,key);return "SOLICITADO".equals(v.status())?dispatcher.dispatch(id,false):v;}
    @PostMapping("/payouts/{id}/reconcile") PayoutView reconcile(Authentication a,HttpServletRequest r,@PathVariable UUID id,@RequestHeader(value="Idempotency-Key",required=false)String key){mutation(a,r);PayoutView v=service.reconcile(principal(a),id,key);return "CONFIRMADO".equals(v.status())?v:dispatcher.dispatch(id,true);}
    private AuthService.SessionPrincipal principal(Authentication a){return (AuthService.SessionPrincipal)a.getDetails();}
    private void mutation(Authentication a,HttpServletRequest r){if("WEB".equals(principal(a).client())&&!origin.equals(r.getHeader("Origin")))throw OrderService.error(org.springframework.http.HttpStatus.FORBIDDEN,"FORBIDDEN","Origem inválida.");}
}
