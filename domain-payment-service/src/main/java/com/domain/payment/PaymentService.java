package com.domain.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** Owns immutable prices, durable idempotency, balanced money records and entitlement creation. */
@Service
public class PaymentService {
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    private final ProviderGateway gateway;
    private final Transport http;
    private final ObjectMapper json;
    private final String listing,key;
    private final BigDecimal featured,subscription;
    /** Injects payment storage and server-selected offers. */
    public PaymentService(final JdbcTemplate db,final org.springframework.transaction.PlatformTransactionManager manager,final ProviderGateway gateway,final Transport http,final ObjectMapper json,
            @Value("${app.listing-url}") final String listing,@Value("${app.internal-key}") final String key,
            @Value("${app.featured-price}") final BigDecimal featured,@Value("${app.subscription-price}") final BigDecimal subscription){
        this.db=db;this.tx=new TransactionTemplate(manager);this.gateway=gateway;this.http=http;this.json=json;this.listing=listing;this.key=key;this.featured=featured;this.subscription=subscription;
        if(featured.signum()<=0||subscription.signum()<=0)throw new IllegalArgumentException("Offer prices must be positive");
    }
    /** Describes configured products without allowing the caller to supply an amount. */
    public Map<String,Object> offers(){return Map.of("provider",gateway.name(),"items",List.of(Map.of("id","FEATURED7","amount",featured,"currency","MNT","days",7),Map.of("id","AGENT30","amount",subscription,"currency","MNT","days",30,"credits",5)));}
    /** Reserves a request before any provider write, preventing retries after an uncertain network result. */
    public Map<String,Object> create(final UUID user,final UUID id,final String offer,final UUID property){
        if(!List.of("FEATURED7","AGENT30").contains(offer)||offer.equals("FEATURED7")&&property==null||offer.equals("AGENT30")&&property!=null)throw new IllegalArgumentException("Invalid offer target");
        final String fingerprint=digest(user+"|"+offer+"|"+property);
        final String nonce=UUID.randomUUID().toString()+UUID.randomUUID();
        final boolean inserted=Boolean.TRUE.equals(tx.execute(status->{
            lock(id);final var rows=db.queryForList("SELECT * FROM payment_orders WHERE id=?",id);
            if(!rows.isEmpty()){if(!rows.get(0).get("request_hash").equals(fingerprint))throw conflict();return false;}
            final String provider=gateway.name();if(property!=null)target(user,property);
            db.update("INSERT INTO payment_orders(id,user_id,offer,property_id,request_hash,amount,provider,status,callback_hash) VALUES(?,?,?,?,?,?,?,'CREATING',?)",id,user,offer,property,fingerprint,offer.equals("FEATURED7")?featured:subscription,provider,digest(nonce));return true;
        }));
        if(inserted){
            try{final var invoice=gateway.create(id,user,offer.equals("FEATURED7")?featured:subscription,nonce);
                final Object invoiceId=invoice.get("invoice_id");if(!(invoiceId instanceof String value)||value.isBlank())throw new IllegalStateException("Missing provider invoice");
                db.update("UPDATE payment_orders SET invoice_id=?,provider_payload=CAST(? AS jsonb),status='PENDING',updated_at=now() WHERE id=? AND status='CREATING'",invoiceId,json.writeValueAsString(publicInvoice(invoice)),id);
            }catch(Exception uncertain){db.update("UPDATE payment_orders SET status='CREATE_UNKNOWN',updated_at=now() WHERE id=? AND status='CREATING'",id);}
        }
        return get(user,id);
    }
    /** Lists bounded principal-scoped history and active subscription credits. */
    public Map<String,Object> history(final UUID user){return Map.of("items",db.queryForList("SELECT id,offer,property_id AS \"propertyId\",amount,currency,provider,status,created_at AS \"createdAt\" FROM payment_orders WHERE user_id=? ORDER BY created_at DESC LIMIT 50",user),
            "subscriptions",db.queryForList("SELECT s.id,s.expires_at AS \"expiresAt\",s.remaining FROM subscriptions s JOIN payment_orders p ON p.id=s.id WHERE s.user_id=? AND s.expires_at>now() AND p.status='PAID' ORDER BY s.expires_at",user));}
    /** Returns bounded administrator history with no provider secrets. */
    public Map<String,Object> adminHistory(){return Map.of("items",db.queryForList("SELECT id,offer,property_id AS \"propertyId\",amount,currency,provider,status,created_at AS \"createdAt\" FROM payment_orders ORDER BY created_at DESC LIMIT 50"),"subscriptions",List.of());}
    /** Gets an order through an administrator-authorized caller. */
    public Map<String,Object> adminGet(final UUID id){final var rows=db.queryForList("SELECT user_id FROM payment_orders WHERE id=?",id);if(rows.isEmpty())throw missing();return get((UUID)rows.get(0).get("user_id"),id);}
    /** Never returns callback secrets, provider auth tokens, or internal fingerprints. */
    public Map<String,Object> get(final UUID user,final UUID id){
        final var rows=db.queryForList("SELECT id,offer,property_id AS \"propertyId\",amount,currency,provider,status,provider_payload AS invoice,created_at AS \"createdAt\" FROM payment_orders WHERE id=? AND user_id=?",id,user);
        if(rows.isEmpty())throw missing();final var result=rows.get(0);final Object payload=result.get("invoice");
        if(payload!=null)try{result.put("invoice",json.readValue(payload.toString(),Map.class));}catch(Exception invalid){throw new IllegalStateException("Stored invoice unavailable");}
        return result;
    }
    /** Callback credentials authorize only a reconciliation hint, never a paid state. */
    public void callback(final UUID id,final String nonce){
        final var rows=db.queryForList("SELECT callback_hash FROM payment_orders WHERE id=?",id);
        if(rows.isEmpty()||!MessageDigest.isEqual(digest(nonce).getBytes(StandardCharsets.UTF_8),rows.get(0).get("callback_hash").toString().getBytes(StandardCharsets.UTF_8)))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Invalid callback");
        db.update("UPDATE payment_orders SET callback_seen=true WHERE id=?",id);check(null,id,false);
    }
    /** Verifies only callback-triggered QPay evidence; local tests are explicit and admin-authorized. */
    public Map<String,Object> check(final UUID user,final UUID id,final boolean simulate){
        tx.executeWithoutResult(status->{
            final var row=ownedLocked(user,id);if(row.get("status").equals("PAID"))return;
            if(!row.get("status").equals("PENDING"))throw conflict();
            final boolean local=row.get("provider").equals("LOCAL_TEST");
            if(local&&!simulate||!local&&simulate)throw conflict();
            if(!local&&!Boolean.TRUE.equals(row.get("callback_seen")))throw conflict();
            final var receipt=local?new ProviderGateway.Receipt("TEST-"+id,(BigDecimal)row.get("amount")):gateway.check((String)row.get("invoice_id"));
            if(receipt==null)return;
            if(receipt.amount().compareTo((BigDecimal)row.get("amount"))!=0)throw conflict();
            for(final String receiptId:receipt.id().split(","))db.update("INSERT INTO provider_receipts(receipt_id,order_id) VALUES(?,?)",receiptId,id);
            db.update("UPDATE payment_orders SET status='PAID',receipt_id=?,updated_at=now() WHERE id=?",receipt.id(),id);
            ledger(id,"SALE",(BigDecimal)row.get("amount"));
            if(row.get("offer").equals("AGENT30"))db.update("INSERT INTO subscriptions(id,user_id,expires_at,remaining) VALUES(?,?,now()+interval '30 days',5)",id,row.get("user_id"));
            else grant(id,(UUID)row.get("property_id"),(UUID)row.get("user_id"),Instant.now().plusSeconds(7L*86400));
        });
        final UUID owner=user==null?db.queryForObject("SELECT user_id FROM payment_orders WHERE id=?",UUID.class,id):user;return get(owner,id);
    }
    /** Cancels an unpaid order once and retains uncertain provider outcomes for reconciliation. */
    public Map<String,Object> cancel(final UUID user,final UUID id){
        tx.executeWithoutResult(status->{final var row=ownedLocked(user,id);if(row.get("status").equals("CANCELLED"))return;if(!row.get("status").equals("PENDING"))throw conflict();
            try{gateway.cancel((String)row.get("invoice_id"));db.update("UPDATE payment_orders SET status='CANCELLED',updated_at=now() WHERE id=?",id);}
            catch(Exception unknown){db.update("UPDATE payment_orders SET status='CANCEL_UNKNOWN',updated_at=now() WHERE id=?",id);}});return get(user,id);
    }
    /** Consumes exactly one active subscription credit for an owned, available MNT listing. */
    public Map<String,Object> credit(final UUID user,final UUID id,final UUID property){
        tx.executeWithoutResult(status->{lock(id);final var replay=db.queryForList("SELECT * FROM credit_usage WHERE id=?",id);
            if(!replay.isEmpty()){if(!user.equals(replay.get(0).get("user_id"))||!property.equals(replay.get(0).get("property_id")))throw conflict();return;}
            target(user,property);
            final var plans=db.queryForList("SELECT s.* FROM subscriptions s JOIN payment_orders p ON p.id=s.id WHERE s.user_id=? AND s.expires_at>now() AND s.remaining>0 AND p.status='PAID' ORDER BY s.expires_at LIMIT 1 FOR UPDATE OF s",user);
            if(plans.isEmpty())throw conflict();final var plan=plans.get(0);
            db.update("UPDATE subscriptions SET remaining=remaining-1 WHERE id=?",plan.get("id"));db.update("INSERT INTO credit_usage(id,user_id,subscription_id,property_id) VALUES(?,?,?,?)",id,user,plan.get("id"),property);
            final Instant expiry=((Timestamp)plan.get("expires_at")).toInstant();grant(id,property,user,expiry.isBefore(Instant.now().plusSeconds(7L*86400))?expiry:Instant.now().plusSeconds(7L*86400));
        });return Map.of("id",id,"status","accepted");
    }
    /** Records a provider-confirmed refund with balancing entries and durable entitlement revocation. */
    public Map<String,Object> refund(final UUID id){
        tx.executeWithoutResult(status->{final var row=ownedLocked(null,id);if(row.get("status").equals("REFUNDED"))return;if(!row.get("status").equals("PAID"))throw conflict();
            // Used subscription credits require manual support review instead of silently refunding consumed service.
            if(db.queryForObject("SELECT count(*) FROM credit_usage WHERE subscription_id=?",Long.class,id)>0)throw conflict();
            try{gateway.refund((String)row.get("receipt_id"));db.update("UPDATE payment_orders SET status='REFUNDED',updated_at=now() WHERE id=?",id);ledger(id,"REFUND",((BigDecimal)row.get("amount")).negate());
                db.update("UPDATE subscriptions SET expires_at=now(),remaining=0 WHERE id=?",id);
                db.update("UPDATE entitlement_outbox SET revoked=true,delivered_at=NULL,next_attempt=now() WHERE id=?",id);
            }catch(ResponseStatusException failure){throw failure;}catch(Exception unknown){db.update("UPDATE payment_orders SET status='REFUND_UNKNOWN',updated_at=now() WHERE id=?",id);}});
        return get(db.queryForObject("SELECT user_id FROM payment_orders WHERE id=?",UUID.class,id),id);
    }
    /** Locks a principal-owned order so callback and manual reconciliation cannot double-book. */
    private Map<String,Object> ownedLocked(final UUID user,final UUID id){final var rows=db.queryForList("SELECT * FROM payment_orders WHERE id=? FOR UPDATE",id);if(rows.isEmpty())throw missing();final var row=rows.get(0);if(user!=null&&!user.equals(row.get("user_id")))throw missing();return row;}
    /** Resolves eligibility over the authenticated internal listing boundary. */
    private void target(final UUID user,final UUID property){final var row=http.send(listing+"/internal/properties/"+property+"/eligibility","GET",null,"X-Internal-Key",key);if(!user.toString().equals(row.get("agentId"))||!"ACTIVE".equals(row.get("status"))||!"MNT".equals(row.get("currency")))throw conflict();}
    /** Appends both sides of one accounting transaction; PostgreSQL enforces zero balance at commit. */
    private void ledger(final UUID id,final String kind,final BigDecimal amount){db.update("INSERT INTO ledger(id,order_id,kind,account,amount) VALUES(?,?,?,'MERCHANT_CLEARING',?),(?,?,?,'SALES',?)",UUID.randomUUID(),id,kind,amount,UUID.randomUUID(),id,kind,amount.negate());}
    /** Stores entitlement delivery in the same transaction as paid state or credit consumption. */
    private void grant(final UUID id,final UUID property,final UUID user,final Instant expiry){db.update("INSERT INTO entitlement_outbox(id,property_id,user_id,expires_at) VALUES(?,?,?,?)",id,property,user,Timestamp.from(expiry));}
    /** Restricts customer-visible invoice data to documented QR/deeplink fields. */
    private Map<String,Object> publicInvoice(final Map<String,Object> invoice){final var safe=new LinkedHashMap<String,Object>();for(final String field:List.of("invoice_id","qr_text","qr_image","urls","qPay_shortUrl","test","message"))if(invoice.containsKey(field))safe.put(field,invoice.get(field));return safe;}
    /** Serializes identical keys before checking persisted fingerprints. */
    private void lock(final UUID id){db.queryForObject("SELECT pg_advisory_xact_lock(?)",Object.class,id.getMostSignificantBits()^id.getLeastSignificantBits());}
    /** Derives a stable replay or callback fingerprint. */
    private String digest(final String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception impossible){throw new IllegalStateException(impossible);}}
    /** Produces a safe state-conflict response. */
    private ResponseStatusException conflict(){return new ResponseStatusException(HttpStatus.CONFLICT,"Payment state conflict");}
    /** Withholds private order existence from other accounts. */
    private ResponseStatusException missing(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Order unavailable");}
}
