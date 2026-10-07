package com.domain.identity.web;

import com.domain.identity.application.AccountService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** Exposes self-service account APIs without letting a client select a principal or role. */
@RestController
public class AccountController {
    private final AccountService accounts;
    private final String internalKey;
    /** Injects account use cases and an optional mandatory-on-use service key. */
    public AccountController(final AccountService accounts, @Value("${app.internal-key:}") final String internalKey) {
        this.accounts = accounts; this.internalKey = internalKey;
    }
    /** Returns the caller's profile. */
    @GetMapping("/v1/users/me")
    public Map<String,Object> profile(@AuthenticationPrincipal final Jwt jwt) { return accounts.profile(UUID.fromString(jwt.getSubject())); }
    /** Updates the caller's contact details. */
    @PutMapping("/v1/users/me")
    public Map<String,Object> update(@AuthenticationPrincipal final Jwt jwt, @Valid @RequestBody final Profile request) {
        return accounts.update(UUID.fromString(jwt.getSubject()), request.displayName(), request.phone());
    }
    /** Requests verification only for the authenticated address. */
    @PostMapping("/v1/users/me/verification")
    public Map<String,String> verification(@AuthenticationPrincipal final Jwt jwt) { accounts.requestVerification(UUID.fromString(jwt.getSubject())); return accepted(); }
    /** Confirms email possession with a single-use challenge. */
    @PostMapping("/v1/accounts/verify-email")
    public Map<String,String> verify(@Valid @RequestBody final Challenge request) { accounts.verify(request.token()); return accepted(); }
    /** Returns a uniform recovery response. */
    @PostMapping("/v1/accounts/forgot-password")
    public Map<String,String> forgot(@Valid @RequestBody final Address request) { accounts.requestReset(request.email()); return accepted(); }
    /** Changes a password with a valid recovery challenge. */
    @PostMapping("/v1/accounts/reset-password")
    public Map<String,String> reset(@Valid @RequestBody final Reset request) { accounts.reset(request.token(),request.password()); return accepted(); }
    /** Exposes only voluntarily supplied public agent contact fields. */
    @GetMapping("/v1/agents/{id}/contact")
    public Map<String,Object> contact(@PathVariable final UUID id) { return accounts.agentContact(id); }
    /** Provides revocation epochs only to authenticated service callers. */
    @GetMapping("/internal/accounts/{id}/version")
    public Map<String,Long> version(@PathVariable final UUID id, @RequestHeader(value="X-Internal-Key",defaultValue="") final String key) {
        authorizeInternal(key); return Map.of("version",accounts.authVersion(id));
    }
    /** Enqueues account notifications from the authenticated internal search service. */
    @PostMapping("/internal/notifications")
    public Map<String,String> notify(@RequestHeader(value="X-Internal-Key",defaultValue="") final String key,
            @Valid @RequestBody final Notification request) {
        authorizeInternal(key); final var profile=accounts.profile(request.userId());
        accounts.enqueue(request.notificationId()==null?UUID.randomUUID():request.notificationId(),(String)profile.get("email"),request.subject(),request.body()); return accepted();
    }
    /** Rejects absent keys and compares service credentials without string timing differences. */
    private void authorizeInternal(final String key) {
        if (internalKey.isBlank() || !MessageDigest.isEqual(internalKey.getBytes(StandardCharsets.UTF_8), key.getBytes(StandardCharsets.UTF_8)))
            throw new AccessDeniedException("Invalid service credential");
    }
    /** Uses a fixed response with no token or account existence disclosure. */
    private Map<String,String> accepted() { return Map.of("message","Хүсэлтийг хүлээн авлаа."); }
    public record Profile(@NotNull @Size(max=120) String displayName, @NotNull @Size(max=30) String phone) {}
    public record Address(@NotBlank @Email @Size(max=320) String email) {}
    public record Challenge(@NotBlank @Size(max=128) String token) { @Override public String toString(){return "Challenge[redacted]";} }
    public record Reset(@NotBlank @Size(max=128) String token,@NotBlank @Size(max=72) String password) { @Override public String toString(){return "Reset[redacted]";} }
    public record Notification(UUID notificationId,@NotNull UUID userId,@NotBlank @Size(max=200) String subject,@NotBlank @Size(max=3000) String body) {}
}
