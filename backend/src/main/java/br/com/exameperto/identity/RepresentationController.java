package br.com.exameperto.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
class RepresentationController {
    private final RepresentationService service;
    private final String origin;
    RepresentationController(RepresentationService service,
        @Value("${registration.allowed-origin:http://localhost:5173}") String origin) {
        this.service=service; this.origin=origin;
    }

    @PostMapping("/me/patient")
    ResponseEntity<PatientView> createPatient(Authentication authentication, HttpServletRequest request,
                                               @Valid @RequestBody PatientInput input) {
        protectMutation(authentication,request);
        return withEtag(HttpStatus.CREATED,service.createPatient(principal(authentication).userId(),input));
    }

    @GetMapping("/me/patient")
    ResponseEntity<PatientView> ownPatient(Authentication authentication) {
        return withEtag(HttpStatus.OK,service.ownPatient(principal(authentication).userId()));
    }

    @PutMapping("/me/patient")
    ResponseEntity<PatientView> updatePatient(Authentication authentication, HttpServletRequest request,
        @RequestHeader(value="If-Match",required=false) String ifMatch, @Valid @RequestBody PatientInput input) {
        protectMutation(authentication,request);
        return withEtag(HttpStatus.OK,service.updatePatient(principal(authentication).userId(),input,version(ifMatch)));
    }

    @GetMapping("/patients/{patientId}")
    ResponseEntity<PatientView> patient(Authentication authentication, @PathVariable UUID patientId,
                                        @RequestParam(required=false) FamilyScope scope) {
        return withEtag(HttpStatus.OK,service.accessiblePatient(principal(authentication).userId(),patientId,scope));
    }

    @PostMapping("/patients/{patientId}/invitations")
    ResponseEntity<FamilyInvitationView> invite(Authentication authentication, HttpServletRequest request,
        @PathVariable UUID patientId, @Valid @RequestBody FamilyInvitationInput input) {
        protectMutation(authentication,request);
        return withEtag(HttpStatus.CREATED,service.invite(principal(authentication).userId(),patientId,input));
    }

    @GetMapping("/patients/{patientId}/invitations")
    List<FamilyInvitationView> invitations(Authentication authentication,@PathVariable UUID patientId) {
        return service.listInvitations(principal(authentication).userId(),patientId);
    }

    @GetMapping("/me/family-invitations")
    List<FamilyInvitationView> received(Authentication authentication) {
        return service.receivedInvitations(principal(authentication).userId());
    }

    @GetMapping("/family-invitations/{invitationId}")
    ResponseEntity<FamilyInvitationView> invitation(Authentication authentication,@PathVariable UUID invitationId) {
        return withEtag(HttpStatus.OK,service.getInvitation(principal(authentication).userId(),invitationId));
    }

    @PostMapping("/family-invitations/{invitationId}/accept")
    ResponseEntity<FamilyInvitationView> accept(Authentication authentication,HttpServletRequest request,
        @PathVariable UUID invitationId,@RequestHeader(value="If-Match",required=false) String ifMatch,
        @Valid @RequestBody AcceptInvitationInput input) {
        protectMutation(authentication,request);
        return withEtag(HttpStatus.OK,service.accept(principal(authentication).userId(),invitationId,input,version(ifMatch)));
    }

    @PostMapping("/family-invitations/{invitationId}/revoke")
    ResponseEntity<FamilyInvitationView> revokeInvitation(Authentication authentication,HttpServletRequest request,
        @PathVariable UUID invitationId,@RequestHeader(value="If-Match",required=false) String ifMatch) {
        protectMutation(authentication,request);
        return withEtag(HttpStatus.OK,service.revokeInvitation(principal(authentication).userId(),invitationId,version(ifMatch)));
    }

    @PostMapping("/patients/{patientId}/grants")
    ResponseEntity<GrantView> confirm(Authentication authentication,HttpServletRequest request,
        @PathVariable UUID patientId,@Valid @RequestBody ConfirmGrantInput input) {
        protectMutation(authentication,request);
        return withEtag(HttpStatus.CREATED,service.confirm(principal(authentication).userId(),patientId,input));
    }

    @GetMapping("/patients/{patientId}/grants")
    GrantPageView grants(Authentication authentication,@PathVariable UUID patientId) {
        return new GrantPageView(service.listGrants(principal(authentication).userId(),patientId),null);
    }

    @GetMapping("/me/family-authorizations")
    GrantPageView familyAuthorizations(Authentication authentication) {
        return new GrantPageView(service.listEffectiveForFamily(principal(authentication).userId()),null);
    }

    @DeleteMapping("/patients/{patientId}/grants/{grantId}")
    ResponseEntity<Void> revokeGrant(Authentication authentication,HttpServletRequest request,
        @PathVariable UUID patientId,@PathVariable UUID grantId,
        @RequestHeader(value="If-Match",required=false) String ifMatch) {
        protectMutation(authentication,request);
        service.revokeGrant(principal(authentication).userId(),patientId,grantId,version(ifMatch));
        return ResponseEntity.noContent().build();
    }

    private AuthService.SessionPrincipal principal(Authentication auth) { return (AuthService.SessionPrincipal)auth.getDetails(); }
    private void protectMutation(Authentication auth,HttpServletRequest request) {
        if (principal(auth).client().equals("WEB") && !origin.equals(request.getHeader("Origin")))
            throw new RepresentationService.RepresentationException(HttpStatus.FORBIDDEN,"FORBIDDEN","Origem inválida.",false);
    }
    private long version(String value) {
        if (value==null) throw new RepresentationService.RepresentationException(HttpStatus.PRECONDITION_REQUIRED,"PRECONDITION_REQUIRED","Informe If-Match.",false);
        if (!value.matches("\"[0-9]+\"")) throw new RepresentationService.RepresentationException(HttpStatus.BAD_REQUEST,"INVALID_INPUT","If-Match inválido.",false);
        try { return Long.parseLong(value.substring(1,value.length()-1)); }
        catch (NumberFormatException ex) { throw new RepresentationService.RepresentationException(HttpStatus.BAD_REQUEST,"INVALID_INPUT","If-Match inválido.",false); }
    }
    private <T> ResponseEntity<T> withEtag(HttpStatus status,T body) {
        long version=body instanceof PatientView p?p.version():body instanceof FamilyInvitationView i?i.version():((GrantView)body).version();
        return ResponseEntity.status(status).header(HttpHeaders.ETAG,"\""+version+"\"").header(HttpHeaders.CACHE_CONTROL,"no-store").body(body);
    }
}
