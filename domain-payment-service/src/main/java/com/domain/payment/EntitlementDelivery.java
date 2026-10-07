package com.domain.payment;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
/** Publishes grant/revoke operations from the payment transaction's durable outbox. */
@Component
public class EntitlementDelivery {
    private final JdbcTemplate db;
    private final Transport http;
    private final String listing,key;
    /** Supplies private transport and payment storage. */
    public EntitlementDelivery(final JdbcTemplate db,final Transport http,@Value("${app.listing-url}") final String listing,@Value("${app.internal-key}") final String key){this.db=db;this.http=http;this.listing=listing;this.key=key;}
    /** Replays stable grant IDs without extending their original expiry. */
    @Scheduled(fixedDelay=2000) @Transactional
    public void deliver(){db.update("UPDATE payment_orders SET status='CREATE_UNKNOWN',updated_at=now() WHERE status='CREATING' AND created_at<now()-interval '5 minutes'");for(final var row:db.queryForList("SELECT * FROM entitlement_outbox WHERE delivered_at IS NULL AND next_attempt<=now() ORDER BY next_attempt LIMIT 5 FOR UPDATE SKIP LOCKED")){
        try{http.send(listing+"/internal/featured/"+row.get("id"),"PUT",Map.of("propertyId",row.get("property_id"),"agentId",row.get("user_id"),"expiresAt",((java.sql.Timestamp)row.get("expires_at")).toInstant().toString(),"revoked",row.get("revoked")),"X-Internal-Key",key);
            db.update("UPDATE entitlement_outbox SET delivered_at=now() WHERE id=?",row.get("id"));}
        catch(Exception deferred){db.update("UPDATE entitlement_outbox SET attempts=attempts+1,next_attempt=now()+(LEAST(300,power(2,LEAST(attempts,8)))*interval '1 second') WHERE id=?",row.get("id"));}
    }}
}
