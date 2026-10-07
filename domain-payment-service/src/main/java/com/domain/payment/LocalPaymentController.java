package com.domain.payment;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
/** Exposes explicit local simulation only in the development profile and to administrators. */
@RestController @Profile("dev")
public class LocalPaymentController {
    private final PaymentService service;
    /** Shares the normal ledger and entitlement flow so simulations exercise real persistence. */
    public LocalPaymentController(final PaymentService service){this.service=service;}
    /** Confirms a test invoice; this endpoint cannot settle QPay invoices. */
    @PostMapping("/v1/admin/payments/{id}/simulate") @PreAuthorize("hasRole('AGENCY_ADMIN')")
    public Map<String,Object> simulate(@PathVariable final UUID id){return service.check(null,id,true);}
}
