package com.domain.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.Clock;
import java.util.UUID;

/** Account record owned exclusively by the Identity bounded context. */
@Entity
@Table(name = "platform_users")
public class PlatformUser {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 320)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AccountRole role;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Required by JPA. */
    protected PlatformUser() {
    }

    /** Creates a new enabled platform account with a normalized email and BCrypt hash. */
    public static PlatformUser create(
            final String email,
            final String passwordHash,
            final AccountRole role,
            final Clock clock) {
        final PlatformUser user = new PlatformUser();
        user.id = UUID.randomUUID();
        user.email = email;
        user.passwordHash = passwordHash;
        user.role = role;
        user.enabled = true;
        user.createdAt = Instant.now(clock);
        user.updatedAt = user.createdAt;
        return user;
    }

    /** @return immutable account identifier used as the JWT subject. */
    public UUID getId() { return id; }
    /** @return login email address. */
    public String getEmail() { return email; }
    /** @return BCrypt password hash. */
    public String getPasswordHash() { return passwordHash; }
    /** @return platform role. */
    public AccountRole getRole() { return role; }
    /** @return whether the account can authenticate. */
    public boolean isEnabled() { return enabled; }
    /** @return account creation time. */
    public Instant getCreatedAt() { return createdAt; }
}
