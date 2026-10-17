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
  @GetMapping("/{type}/{id}") OperationalPolicyDetailView detail(Authentication a,@PathVariable String type,@PathVariable UUID id){return service.detail(principal(a),type.toUpperCase(),id);}
  @GetMapping("/{type}/{id}/history") List<OperationalPolicyHistoryView> history(Authentication a,@PathVariable String type,@PathVariable UUID id){return service.history(principal(a),type.toUpperCase(),id);}
  @GetMapping("/incidents") List<OperationalIncidentView> incidents(Authentication a){return service.incidents(principal(a));}
  @PostMapping("/incidents/{id}/resolve") IncidentView resolve(Authentication a,@PathVariable UUID id,@Valid @RequestBody IncidentResolutionInput input){return service.resolve(principal(a),id,input);}
  @PostMapping("/tariffs") OperationalPolicyView tariff(Authentication a,@Valid @RequestBody TariffPolicyInput in){return service.createTariff(principal(a),in);}
  @PutMapping("/tariffs/{id}") OperationalPolicyView editTariff(Authentication a,@PathVariable UUID id,@Valid @RequestBody TariffPolicyInput in){return service.editTariff(principal(a),id,in);}
  @PostMapping("/tariffs/{id}/publish") OperationalPolicyView publish(Authentication a,@PathVariable UUID id){return service.publishTariff(principal(a),id);}
  @PostMapping("/tariffs/{id}/deactivate") OperationalPolicyView deactivate(Authentication a,@PathVariable UUID id){return service.deactivateTariff(principal(a),id);}
  @PostMapping("/cancellations") OperationalPolicyView cancellation(Authentication a,@Valid @RequestBody CancellationPolicyInput in){return service.createCancellation(principal(a),in);}
  @PostMapping("/designations") OperationalPolicyView designation(Authentication a,@Valid @RequestBody DesignationPolicyInput in){return service.createDesignation(principal(a),in);}
  @PostMapping("/{type}/{id}/publish") OperationalPolicyView publish(Authentication a,@PathVariable String type,@PathVariable UUID id){return service.publish(principal(a),type.toUpperCase(),id);}
  @PostMapping("/{type}/{id}/deactivate") OperationalPolicyView deactivate(Authentication a,@PathVariable String type,@PathVariable UUID id){return service.deactivate(principal(a),type.toUpperCase(),id);}
  private AuthService.SessionPrincipal principal(Authentication a){return (AuthService.SessionPrincipal)a.getDetails();}
}
