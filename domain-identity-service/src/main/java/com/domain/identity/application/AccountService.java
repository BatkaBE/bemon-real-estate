package com.domain.identity.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns profile data and expiring single-use email/password recovery challenges. */
@Service
public class AccountService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate db;
    private final PasswordEncoder passwords;
    private final Clock clock;
    private final String webOrigin;

    /** Supplies account persistence, crypto, time, and trusted link origin. */
    public AccountService(final JdbcTemplate db, final PasswordEncoder passwords, final Clock clock,
            @Value("${app.web-origin:http://localhost:3000}") final String webOrigin) {
        this.db = db; this.passwords = passwords; this.clock = clock; this.webOrigin = webOrigin;
    }

    /** Returns only the authenticated account's safe profile fields. */
    public Map<String, Object> profile(final UUID userId) {
        return db.queryForMap("SELECT id,email,role,display_name AS \"displayName\",phone,"
                + "email_verified AS \"emailVerified\",auth_version AS \"authVersion\" FROM platform_users WHERE id=? AND enabled", userId);
    }

    /** Updates optional contact details without changing email, credentials, or role. */
    @Transactional
    public Map<String, Object> update(final UUID userId, final String displayName, final String phone) {
        if (displayName.length() > 120 || !phone.matches("[+0-9 ()-]{0,30}")) throw new IllegalArgumentException("Invalid profile");
        db.update("UPDATE platform_users SET display_name=?,phone=?,updated_at=? WHERE id=? AND enabled",
                displayName.strip(), phone.strip(), java.sql.Timestamp.from(Instant.now(clock)), userId);
        return profile(userId);
    }

    /** Returns explicitly supplied contact data for enabled agents only. */
    public Map<String, Object> agentContact(final UUID userId) {
        final var rows = db.queryForList("SELECT id,display_name AS \"displayName\",phone FROM platform_users "
                + "WHERE id=? AND enabled AND role='ROLE_AGENT'", userId);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    /** Enqueues verification for an authenticated account; repeated challenges invalidate prior links. */
    @Transactional
    public void requestVerification(final UUID userId) {
        final var user = db.queryForMap("SELECT email,email_verified FROM platform_users WHERE id=? AND enabled FOR UPDATE", userId);
        if (Boolean.TRUE.equals(user.get("email_verified"))) return;
        issue(userId, (String) user.get("email"), "VERIFY");
    }

    /** Always returns the same public outcome so recovery cannot enumerate registered accounts. */
    @Transactional
    public void requestReset(final String email) {
        final var users = db.queryForList("SELECT id,email FROM platform_users WHERE lower(email)=? AND enabled FOR UPDATE",
                email.strip().toLowerCase(Locale.ROOT));
        if (!users.isEmpty()) issue((UUID) users.get(0).get("id"), (String) users.get(0).get("email"), "RESET");
    }

    /** Verifies a token once and records email ownership without exposing the token in logs. */
    @Transactional
    public void verify(final String token) {
        final UUID userId = consume(token, "VERIFY");
        db.update("UPDATE platform_users SET email_verified=TRUE,updated_at=? WHERE id=?", java.sql.Timestamp.from(Instant.now(clock)), userId);
    }

    /** Changes the password once and revokes all OAuth grants for this principal. */
    @Transactional
    public void reset(final String token, final String password) {
        PasswordPolicy.validate(password);
        final UUID userId = consume(token, "RESET");
        db.update("UPDATE platform_users SET password_hash=?,auth_version=auth_version+1,updated_at=? WHERE id=?",
                passwords.encode(password), java.sql.Timestamp.from(Instant.now(clock)), userId);
        db.update("DELETE FROM oauth2_authorization WHERE principal_name=?", userId.toString());
        db.update("DELETE FROM spring_session WHERE principal_name=?", userId.toString());
        db.update("DELETE FROM web_sessions WHERE user_id=?",userId);
        db.update("UPDATE account_tokens SET consumed_at=? WHERE user_id=? AND consumed_at IS NULL", java.sql.Timestamp.from(Instant.now(clock)), userId);
    }

    /** Publishes only the epoch needed by trusted services to reject revoked JWTs. */
    public long authVersion(final UUID userId) {
        final var values = db.queryForList("SELECT auth_version FROM platform_users WHERE id=? AND enabled", Long.class, userId);
        if (values.isEmpty()) throw new IllegalArgumentException("Account unavailable");
        return values.get(0);
    }

    /** Stores a digest and enqueues the raw one-time link only in the private mail outbox. */
    private void issue(final UUID userId, final String email, final String purpose) {
        final byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        final String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        final Instant now = Instant.now(clock);
        db.update("UPDATE account_tokens SET consumed_at=? WHERE user_id=? AND purpose=? AND consumed_at IS NULL", java.sql.Timestamp.from(now), userId, purpose);
        db.update("INSERT INTO account_tokens(token_hash,user_id,purpose,expires_at) VALUES(?,?,?,?)",
                digest(token), userId, purpose, java.sql.Timestamp.from(now.plusSeconds(purpose.equals("RESET") ? 1800 : 86400)));
        final String route = purpose.equals("RESET") ? "/reset-password" : "/verify-email";
        enqueue(email, purpose.equals("RESET") ? "GerHub — нууц үг сэргээх" : "GerHub — имэйл баталгаажуулах",
                "Дараах холбоосоор орно уу. Холбоос нэг удаа үйлчилнэ.\n" + webOrigin + route + "?token=" + token);
    }

    /** Atomically consumes a valid token; concurrent/replayed uses fail. */
    private UUID consume(final String token, final String purpose) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw new IllegalArgumentException("Invalid challenge");
        final var owners = db.queryForList("SELECT user_id FROM account_tokens WHERE token_hash=? AND purpose=?",
                UUID.class, digest(token), purpose);
        if (owners.isEmpty()) throw new IllegalArgumentException("Invalid challenge");
        // All challenge operations lock the account before token rows to avoid reset/reissue deadlocks.
        db.queryForMap("SELECT id FROM platform_users WHERE id=? AND enabled FOR UPDATE", owners.get(0));
        final List<UUID> ids = db.queryForList("UPDATE account_tokens SET consumed_at=? WHERE token_hash=? AND purpose=? "
                + "AND consumed_at IS NULL AND expires_at>? RETURNING user_id", UUID.class,
                java.sql.Timestamp.from(Instant.now(clock)), digest(token), purpose, java.sql.Timestamp.from(Instant.now(clock)));
        if (ids.isEmpty()) throw new IllegalArgumentException("Invalid or expired challenge");
        return ids.get(0);
    }

    /** Queues bounded text mail for asynchronous delivery rather than blocking the API. */
    public void enqueue(final String email, final String subject, final String body) {
        enqueue(UUID.randomUUID(),email,subject,body);
    }

    /** Deduplicates stable internal notification IDs across producer retries. */
    public void enqueue(final UUID id,final String email,final String subject,final String body){
        db.update("INSERT INTO email_outbox(id,recipient,subject,body) VALUES(?,?,?,?) ON CONFLICT DO NOTHING",id,email,subject,body);
    }

    /** Derives a non-reversible token lookup key. */
    private String digest(final String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
