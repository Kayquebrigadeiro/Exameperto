package br.com.exameperto.identity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
@RestController @RequestMapping("/api/v1/order-options")
class GeocodeController {
    private final ConfiguredGeocoder geocoder;
    GeocodeController(ConfiguredGeocoder geocoder){this.geocoder=geocoder;}
    @PostMapping("/geocode") List<GeocodeCandidateView> search(Authentication a,HttpServletRequest request,@Valid @RequestBody GeocodeInput input){
        if(a==null||!a.isAuthenticated()) throw OrderService.error(org.springframework.http.HttpStatus.UNAUTHORIZED,"UNAUTHORIZED","Sessão necessária.");
        return geocoder.search(input.address()).stream().map(c->new GeocodeCandidateView(c.label(),c.latitude(),c.longitude(),c.provider(),c.reference(),c.confirmationToken())).toList();
    }
}
