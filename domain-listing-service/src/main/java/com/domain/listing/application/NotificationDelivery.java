package com.domain.listing.application;

import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Delivers participant notifications with durable backoff and multi-worker row claims. */
@Component @EnableScheduling
@ConditionalOnProperty(name="app.notifications.enabled",havingValue="true")
public class NotificationDelivery {
    private final JdbcTemplate db;
    private final IdentityBridge identity;
    /** Injects the transaction store and bounded internal transport. */
    public NotificationDelivery(final JdbcTemplate db,final IdentityBridge identity){this.db=db;this.identity=identity;}
    /** Marks only acknowledged deliveries, keeping all failures retryable. */
    @Scheduled(fixedDelayString="${app.notifications.poll-ms:3000}") @Transactional
    public void deliver() {
        for(final var row:db.queryForList("SELECT * FROM notification_outbox WHERE delivered_at IS NULL AND next_attempt_at<=now() "
                +"AND attempts<12 ORDER BY created_at LIMIT 5 FOR UPDATE SKIP LOCKED")) {
            try {identity.notify((UUID)row.get("id"),(UUID)row.get("user_id"),(String)row.get("subject"),(String)row.get("body"));
                db.update("UPDATE notification_outbox SET delivered_at=now() WHERE id=?",row.get("id"));}
            catch(Exception deferred){db.update("UPDATE notification_outbox SET attempts=attempts+1,next_attempt_at=now()+"
                    +"(LEAST(3600,power(2,attempts+1)) * interval '1 second') WHERE id=?",row.get("id"));}
        }
    }
}
