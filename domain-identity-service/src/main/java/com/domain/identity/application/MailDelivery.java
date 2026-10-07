package com.domain.identity.application;

import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Delivers durable mail with bounded retries; logs contain neither addresses nor one-time links. */
@Component
@EnableScheduling
@ConditionalOnProperty(name="app.mail.enabled",havingValue="true")
public class MailDelivery {
    private static final Logger LOG = LoggerFactory.getLogger(MailDelivery.class);
    private final JdbcTemplate db;
    private final JavaMailSender sender;
    private final String from;
    /** Supplies SMTP transport and the private outbox. */
    public MailDelivery(final JdbcTemplate db,final JavaMailSender sender,@Value("${app.mail.from:no-reply@bemon.local}") final String from) {
        this.db=db;this.sender=sender;this.from=from;
    }
    /** Competing workers claim rows in one transaction; a crash can duplicate an email, never a token use. */
    @Scheduled(fixedDelayString="${app.mail.poll-ms:2000}")
    @Transactional
    public void deliver() {
        for(final var mail:db.queryForList("SELECT * FROM email_outbox WHERE sent_at IS NULL AND next_attempt_at<=now() "
                +"AND attempts<12 ORDER BY created_at LIMIT 10 FOR UPDATE SKIP LOCKED")) {
            final UUID id=(UUID)mail.get("id");
            try {
                final var message=new SimpleMailMessage();message.setFrom(from);message.setTo((String)mail.get("recipient"));
                message.setSubject((String)mail.get("subject"));message.setText((String)mail.get("body"));sender.send(message);
                db.update("UPDATE email_outbox SET sent_at=now(),body='[delivered]' WHERE id=?",id);
            } catch(Exception failure) {
                final int attempts=((Number)mail.get("attempts")).intValue()+1;
                db.update("UPDATE email_outbox SET attempts=?,next_attempt_at=? WHERE id=?",attempts,
                        java.sql.Timestamp.from(Instant.now().plusSeconds(Math.min(3600,1L<<Math.min(attempts,12)))),id);
                LOG.warn("Email delivery deferred: job={}, attempt={}",id,attempts);
            }
        }
    }
}
