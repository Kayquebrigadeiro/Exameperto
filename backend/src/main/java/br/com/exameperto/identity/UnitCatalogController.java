package br.com.exameperto.identity;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
class UnitCatalogController {
    private final UnitCatalogService service;
    UnitCatalogController(UnitCatalogService service){this.service=service;}
    @GetMapping("/order-options/units") List<AvailableUnitView> available(){return service.available();}
    @GetMapping("/admin/units") List<UnitCatalogView> list(Authentication a){return service.list(principal(a));}
    @PostMapping("/admin/units") UnitCatalogView create(Authentication a,@Valid @RequestBody UnitCatalogInput i){return service.create(principal(a),i);}
    @PutMapping("/admin/units/{id}") UnitCatalogView edit(Authentication a,@PathVariable UUID id,@Valid @RequestBody UnitCatalogInput i){return service.edit(principal(a),id,i);}
    @PostMapping("/admin/units/{id}/activation") UnitCatalogView activate(Authentication a,@PathVariable UUID id,@RequestParam boolean active){return service.setRegistration(principal(a),id,active);}
    @PostMapping("/admin/units/{id}/partnership") UnitCatalogView partnership(Authentication a,@PathVariable UUID id,@Valid @RequestBody PartnershipInput i){return service.partnership(principal(a),id,i);}
    @PostMapping("/admin/units/{id}/protocols") UnitCatalogView protocol(Authentication a,@PathVariable UUID id,@Valid @RequestBody ProtocolInput i){return service.createProtocol(principal(a),id,i);}
    @PutMapping("/admin/units/{id}/protocols/{protocol}") UnitCatalogView editProtocol(Authentication a,@PathVariable UUID id,@PathVariable UUID protocol,@Valid @RequestBody ProtocolInput i){return service.editProtocol(principal(a),id,protocol,i);}
    @PostMapping("/admin/units/{id}/protocols/{protocol}/activation") UnitCatalogView protocolActivation(Authentication a,@PathVariable UUID id,@PathVariable UUID protocol,@RequestParam boolean active){return service.setProtocol(principal(a),id,protocol,active);}
    private AuthService.SessionPrincipal principal(Authentication a){return (AuthService.SessionPrincipal)a.getDetails();}
}
