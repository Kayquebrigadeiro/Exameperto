package br.com.exameperto.identity;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/operational-policies")
class OperationalPolicyController {
  private final OperationalPolicyService service;
  OperationalPolicyController(OperationalPolicyService service){this.service=service;}
  @GetMapping List<OperationalPolicyView> list(Authentication a){return service.list(principal(a));}
  @PostMapping("/tariffs") OperationalPolicyView tariff(Authentication a,@Valid @RequestBody TariffPolicyInput in){return service.createTariff(principal(a),in);}
  @PostMapping("/tariffs/{id}/publish") OperationalPolicyView publish(Authentication a,@PathVariable UUID id){return service.publishTariff(principal(a),id);}
  @PostMapping("/tariffs/{id}/deactivate") OperationalPolicyView deactivate(Authentication a,@PathVariable UUID id){return service.deactivateTariff(principal(a),id);}
  @PostMapping("/cancellations") OperationalPolicyView cancellation(Authentication a,@Valid @RequestBody CancellationPolicyInput in){return service.createCancellation(principal(a),in);}
  @PostMapping("/designations") OperationalPolicyView designation(Authentication a,@Valid @RequestBody DesignationPolicyInput in){return service.createDesignation(principal(a),in);}
  @PostMapping("/{type}/{id}/publish") OperationalPolicyView publish(Authentication a,@PathVariable String type,@PathVariable UUID id){return service.publish(principal(a),type.toUpperCase(),id);}
  @PostMapping("/{type}/{id}/deactivate") OperationalPolicyView deactivate(Authentication a,@PathVariable String type,@PathVariable UUID id){return service.deactivate(principal(a),type.toUpperCase(),id);}
  private AuthService.SessionPrincipal principal(Authentication a){return (AuthService.SessionPrincipal)a.getDetails();}
}
