package com.domain.identity.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Persists encrypted web sessions and cross-process refresh leases behind a service credential. */
@RestController
@RequestMapping("/internal/web-sessions")
public class WebSessionController {
    private final JdbcTemplate db;
    private final String key;
    /** Supplies durable storage and a required-on-use internal credential. */
    public WebSessionController(final JdbcTemplate db,@Value("${app.internal-key:}") final String key) { this.db=db;this.key=key; }
    /** Creates an absolute-lifetime session; only the web encryption key can read the payload. */
    @PostMapping
    public Map<String,String> create(@RequestHeader(value="X-Internal-Key",defaultValue="") final String supplied,
            @Valid @RequestBody final NewSession body) {
        authorize(supplied);
        db.update("INSERT INTO web_sessions(id,user_id,payload,expires_at) VALUES(?,?,?,?)",
                body.id(),body.userId(),body.payload(),java.sql.Timestamp.from(Instant.now().plusSeconds(30L*86400)));
        return Map.of("status","created");
    }
    /** Returns a live encrypted session with no client-controlled principal lookup. */
    @GetMapping("/{id}")
    public Map<String,Object> get(@PathVariable final UUID id,@RequestHeader(value="X-Internal-Key",defaultValue="") final String supplied) {
        authorize(supplied);return live(id);
    }
    /** Atomically elects one refresher across all web replicas. */
    @PostMapping("/{id}/lease")
    public Map<String,Object> lease(@PathVariable final UUID id,@RequestHeader(value="X-Internal-Key",defaultValue="") final String supplied,
            @Valid @RequestBody final Lease body) {
        authorize(supplied);
        final int rows=db.update("UPDATE web_sessions SET lease_id=?,lease_until=now()+interval '45 seconds' WHERE id=? "
                +"AND expires_at>now() AND (lease_until IS NULL OR lease_until<now())",body.leaseId(),id);
        return Map.of("acquired",rows==1,"payload",live(id).get("payload"));
    }
    /** Publishes rotated tokens only if the same refresher still owns the lease. */
    @PutMapping("/{id}")
    public Map<String,String> update(@PathVariable final UUID id,@RequestHeader(value="X-Internal-Key",defaultValue="") final String supplied,
            @Valid @RequestBody final Replacement body) {
        authorize(supplied);
        final int rows=db.update("UPDATE web_sessions SET payload=?,lease_id=NULL,lease_until=NULL WHERE id=? "
                +"AND lease_id=? AND lease_until>now() AND expires_at>now()",body.payload(),id,body.leaseId());
        if(rows!=1) throw new ResponseStatusException(HttpStatus.CONFLICT,"Refresh lease expired");
        return Map.of("status","updated");
    }
    /** Revokes the durable web session on logout. */
    @DeleteMapping("/{id}")
    public Map<String,String> delete(@PathVariable final UUID id,@RequestHeader(value="X-Internal-Key",defaultValue="") final String supplied) {
        authorize(supplied);db.update("DELETE FROM web_sessions WHERE id=?",id);return Map.of("status","deleted");
    }
    /** Distinguishes a missing session without leaking its encrypted content. */
    private Map<String,Object> live(final UUID id) {
        final var rows=db.queryForList("SELECT payload FROM web_sessions WHERE id=? AND expires_at>now()",id);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Session unavailable");
        return rows.get(0);
    }
    /** Fails closed when the internal key is missing. */
    private void authorize(final String supplied) {
        if(key.isBlank()||!MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8)))
            throw new AccessDeniedException("Service credential required");
    }
    public record NewSession(@NotNull UUID id,@NotNull UUID userId,@NotNull @Size(max=12000) String payload) {}
    public record Lease(@NotNull UUID leaseId) {}
    public record Replacement(@NotNull UUID leaseId,@NotNull @Size(max=12000) String payload) {}
}
