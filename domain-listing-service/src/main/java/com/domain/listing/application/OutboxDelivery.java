package com.domain.listing.application;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Delivers committed snapshots at least once and retries only unacknowledged events. */
@Component
@EnableScheduling
@ConditionalOnProperty(name="app.outbox.enabled",havingValue="true")
public class OutboxDelivery {
    private final JdbcTemplate db;
    private final String endpoint;
    private final String key;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    /** Uses a private event boundary and independently persisted delivery progress. */
    public OutboxDelivery(final JdbcTemplate db,@Value("${app.search-url}") final String url,@Value("${app.internal-key}") final String key){this.db=db;this.endpoint=url+"/internal/events";this.key=key;}
    /** Skips rows held by other publishers and marks success only after consumer acknowledgment. */
    @Scheduled(fixedDelay=1000)
    @Transactional
    public void deliver(){
        for(final var row:db.queryForList("SELECT id,payload::text AS payload FROM outbox_events WHERE published_at IS NULL AND next_attempt<=now() ORDER BY occurred_at LIMIT 5 FOR UPDATE SKIP LOCKED")) {
            try{
                final var request=HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(5))
                        .header("X-Internal-Key",key).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString((String)row.get("payload"))).build();
                final int status=http.send(request,HttpResponse.BodyHandlers.discarding()).statusCode();
                if(status<200||status>=300)throw new IllegalStateException("Consumer unavailable");
                db.update("UPDATE outbox_events SET published_at=now() WHERE id=?",row.get("id"));
            }catch(Exception unavailable){
                if(unavailable instanceof InterruptedException)Thread.currentThread().interrupt();
                db.update("UPDATE outbox_events SET attempts=attempts+1,next_attempt=now()+(LEAST(300,power(2,LEAST(attempts,8)))*interval '1 second') WHERE id=?",row.get("id"));
            }
        }
    }
}
