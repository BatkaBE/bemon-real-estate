package com.domain.payment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
/** Exposes immutable offer selection, owner-scoped orders and administrator reconciliation. */
@RestController
public class PaymentController {
    private final PaymentService service;
    /** Supplies auditable payment use cases. */
    public PaymentController(final PaymentService service){this.service=service;}
    /** Returns server-selected prices. */
    @GetMapping("/v1/payments/offers") public Map<String,Object> offers(){return service.offers();}
    /** Creates one invoice for an agent-selected offer, with no caller-supplied amount. */
    @PostMapping("/v1/payments/orders") @PreAuthorize("hasRole('AGENT')")
    public Map<String,Object> create(@AuthenticationPrincipal final Jwt jwt,@RequestHeader("Idempotency-Key") final UUID id,@Valid @RequestBody final Order body){return service.create(UUID.fromString(jwt.getSubject()),id,body.offer(),body.propertyId());}
    /** Returns only the caller's invoices and remaining credits. */
    @GetMapping("/v1/users/me/payments") public Map<String,Object> history(@AuthenticationPrincipal final Jwt jwt){return service.history(UUID.fromString(jwt.getSubject()));}
    /** Retrieves one caller-owned order. */
    @GetMapping("/v1/payments/orders/{id}") public Map<String,Object> get(@AuthenticationPrincipal final Jwt jwt,@PathVariable final UUID id){return service.get(UUID.fromString(jwt.getSubject()),id);}
    /** Manually reconciles only when a real provider callback has already arrived. */
    @PostMapping("/v1/payments/orders/{id}/check") public Map<String,Object> check(@AuthenticationPrincipal final Jwt jwt,@PathVariable final UUID id){return service.check(UUID.fromString(jwt.getSubject()),id,false);}
    /** Cancels only an unpaid caller-owned order. */
    @PostMapping("/v1/payments/orders/{id}/cancel") public Map<String,Object> cancel(@AuthenticationPrincipal final Jwt jwt,@PathVariable final UUID id){return service.cancel(UUID.fromString(jwt.getSubject()),id);}
    /** Consumes one existing credit for a server-verified property. */
    @PostMapping("/v1/payments/credits") @PreAuthorize("hasRole('AGENT')") public Map<String,Object> credit(@AuthenticationPrincipal final Jwt jwt,@RequestHeader("Idempotency-Key") final UUID id,@Valid @RequestBody final Credit body){return service.credit(UUID.fromString(jwt.getSubject()),id,body.propertyId());}
    /** Treats provider callbacks as hints whose secret and provider evidence must both verify. */
    @RequestMapping(value="/v1/payments/callback/{id}",method={RequestMethod.GET,RequestMethod.POST}) public Map<String,String> callback(@PathVariable final UUID id,@RequestParam(defaultValue="") final String token){service.callback(id,token);return Map.of("status","accepted");}
    /** Lists bounded administrator reconciliation history without callback credentials. */
    @GetMapping("/v1/admin/payments") @PreAuthorize("hasRole('AGENCY_ADMIN')") public Map<String,Object> adminHistory(){return service.adminHistory();}
    /** Resolves a private invoice for an authorized administrator's reconciliation screen. */
    @GetMapping("/v1/admin/payments/{id}") @PreAuthorize("hasRole('AGENCY_ADMIN')") public Map<String,Object> adminGet(@PathVariable final UUID id){return service.adminGet(id);}
    /** Performs a provider-backed refund with ledger reversal. */
    @PostMapping("/v1/admin/payments/{id}/refund") @PreAuthorize("hasRole('AGENCY_ADMIN')") public Map<String,Object> refund(@PathVariable final UUID id){return service.refund(id);}
    public record Order(@NotBlank @Size(max=20) String offer,UUID propertyId){}
    public record Credit(@NotNull UUID propertyId){}
}
