package com.domain.listing.application;

import com.domain.listing.domain.model.PropertyNotFoundException;
import com.domain.listing.domain.model.PropertyOwnershipException;
import com.domain.listing.domain.model.StalePropertyVersionException;
import com.domain.listing.domain.model.IdempotencyConflictException;
import com.domain.listing.persistence.IdempotencyLock;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persists principal-scoped favorites and auditable buyer/agent conversations. */
@Service
public class EngagementService {
    private final JdbcTemplate db;
    private final PropertyApplicationService properties;
    private final IdempotencyLock locks;
    private final IdentityBridge identity;
    /** Shares listing transactions while resolving account contact details from Identity. */
    public EngagementService(final JdbcTemplate db,final PropertyApplicationService properties,final IdempotencyLock locks,final IdentityBridge identity) {
        this.db=db;this.properties=properties;this.locks=locks;this.identity=identity;
    }
    /** Adds an available public property once, independently of client-selected user IDs. */
    @Transactional
    public void favorite(final UUID userId,final UUID propertyId) {
        final var visible=db.queryForList("SELECT id FROM properties WHERE id=? AND status='ACTIVE' FOR SHARE",propertyId);
        if(visible.isEmpty()) throw new PropertyNotFoundException(propertyId);
        db.update("INSERT INTO favorites(user_id,property_id) VALUES(?,?) ON CONFLICT DO NOTHING",userId,propertyId);
    }
    /** Removes only the caller's saved relationship; property data stays intact. */
    @Transactional
    public void unfavorite(final UUID userId,final UUID propertyId) {db.update("DELETE FROM favorites WHERE user_id=? AND property_id=?",userId,propertyId);}
    /** Returns whether this caller saved a listing. */
    public boolean isFavorite(final UUID userId,final UUID propertyId) {
        return Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM favorites WHERE user_id=? AND property_id=?)",Boolean.class,userId,propertyId));
    }
    /** Pages saved listings without exposing private or withdrawn properties. */
    @Transactional(readOnly=true)
    public Map<String,Object> favorites(final UUID userId,final String cursor) {
        final var point=cursor==null?null:CursorCodec.decode(cursor);
        var sql="SELECT f.property_id,f.created_at FROM favorites f JOIN properties p ON p.id=f.property_id "
                +"WHERE f.user_id=? AND p.status NOT IN ('DRAFT','WITHDRAWN') ";
        final var args=new java.util.ArrayList<Object>();args.add(userId);
        if(point!=null){sql+="AND (f.created_at,f.property_id)<(?,?) ";args.add(java.sql.Timestamp.from(point.createdAt()));args.add(point.propertyId());}
        final var rows=db.queryForList(sql+"ORDER BY f.created_at DESC,f.property_id DESC LIMIT 21",args.toArray());
        final var page=rows.subList(0,Math.min(20,rows.size()));
        final var items=page.stream().map(row->com.domain.listing.web.dto.PropertyResponse.from(properties.get((UUID)row.get("property_id"),userId))).toList();
        final var result=new java.util.HashMap<String,Object>();result.put("items",items);result.put("pageSize",20);
        result.put("nextCursor",rows.size()>20?cursor(page.get(page.size()-1),"property_id"):null);return result;
    }
    /** Resolves ownership and sender identity on the server, then creates one replay-safe inquiry. */
    @Transactional
    public Map<String,Object> inquire(final UUID buyerId,final UUID propertyId,final UUID key,final String message,final String bearer) {
        locks.acquire(key);
        final String hash=hash(propertyId+"|"+message);
        final var existing=db.queryForList("SELECT * FROM inquiries WHERE id=?",key);
        if(!existing.isEmpty()) {
            if(!buyerId.equals(existing.get(0).get("buyer_id"))||!hash.equals(existing.get(0).get("request_hash"))) throw new IdempotencyConflictException();
            return view(existing.get(0));
        }
        final var property=properties.get(propertyId,null);
        if(property.getStatus()!=com.domain.listing.domain.model.PropertyStatus.ACTIVE) throw new PropertyNotFoundException(propertyId);
        if(property.getAgentId().equals(buyerId)) throw new IllegalArgumentException("Cannot inquire about your own listing");
        final var profile=identity.profile(bearer);
        if(!buyerId.toString().equals(profile.get("id").toString())) throw new PropertyOwnershipException();
        db.update("INSERT INTO inquiries(id,property_id,buyer_id,agent_id,buyer_name,buyer_email,buyer_phone,message,request_hash) VALUES(?,?,?,?,?,?,?,?,?)",
                key,propertyId,buyerId,property.getAgentId(),profile.get("displayName"),profile.get("email"),profile.get("phone"),message,hash);
        notify(property.getAgentId(),"GerHub — шинэ хүсэлт","Таны зарын талаар хүсэлт ирлээ. Dashboard-ийн хүсэлтүүд хэсгээс үзнэ үү.");
        return view(db.queryForMap("SELECT * FROM inquiries WHERE id=?",key));
    }
    /** Lists only a participant's own conversations with stable keyset pagination. */
    public Map<String,Object> inquiries(final UUID userId,final boolean agent,final String cursorValue) {
        final String owner=agent?"agent_id":"buyer_id";final var args=new java.util.ArrayList<Object>();args.add(userId);
        String sql="SELECT i.*,p.title AS property_title FROM inquiries i JOIN properties p ON p.id=i.property_id WHERE i."+owner+"=? ";
        if(cursorValue!=null){final var point=CursorCodec.decode(cursorValue);sql+="AND (i.created_at,i.id)<(?,?) ";args.add(java.sql.Timestamp.from(point.createdAt()));args.add(point.propertyId());}
        final var rows=db.queryForList(sql+"ORDER BY i.created_at DESC,i.id DESC LIMIT 21",args.toArray());
        final var page=rows.subList(0,Math.min(20,rows.size()));final var result=new java.util.HashMap<String,Object>();
        result.put("items",page.stream().map(this::view).toList());result.put("nextCursor",rows.size()>20?cursor(page.get(page.size()-1),"id"):null);return result;
    }
    /** Only the owning agent may respond, and an old version cannot overwrite a later reply. */
    @Transactional
    public Map<String,Object> reply(final UUID agentId,final UUID id,final long version,final String status,final String reply) {
        if(!List.of("OPEN","CONTACTED","CLOSED").contains(status)) throw new IllegalArgumentException("Invalid inquiry status");
        final var rows=db.queryForList("SELECT * FROM inquiries WHERE id=? FOR UPDATE",id);
        if(rows.isEmpty()) throw new PropertyNotFoundException(id);final var row=rows.get(0);
        if(!agentId.equals(row.get("agent_id"))) throw new PropertyOwnershipException();
        if(((Number)row.get("version")).longValue()!=version) throw new StalePropertyVersionException();
        db.update("UPDATE inquiries SET reply=?,status=?,version=version+1,updated_at=now() WHERE id=?",reply,status,id);
        notify((UUID)row.get("buyer_id"),"GerHub — хүсэлтийн хариу","Агент таны хүсэлтэд хариуллаа. Миний хүсэлтүүд хэсгээс үзнэ үү.");
        return view(db.queryForMap("SELECT * FROM inquiries WHERE id=?",id));
    }
    /** Queues notifications atomically with conversation changes. */
    private void notify(final UUID userId,final String subject,final String body) {
        db.update("INSERT INTO notification_outbox(id,user_id,subject,body) VALUES(?,?,?,?)",UUID.randomUUID(),userId,subject,body);
    }
    /** Maps safe names while withholding internal fingerprints. */
    private Map<String,Object> view(final Map<String,Object> row) {
        final var result=new java.util.LinkedHashMap<String,Object>();
        for(final var entry:row.entrySet()) if(!entry.getKey().equals("request_hash")) {
            final String[] words=entry.getKey().split("_");String name=words[0];
            for(int i=1;i<words.length;i++) name+=Character.toUpperCase(words[i].charAt(0))+words[i].substring(1);
            result.put(name,entry.getValue());
        }
        return result;
    }
    /** Encodes an opaque engagement cursor using the same stable timestamp/UUID ordering. */
    private String cursor(final Map<String,Object> row,final String idColumn) {
        return CursorCodec.encode(new com.domain.listing.domain.model.PropertyCursor(((java.sql.Timestamp)row.get("created_at")).toInstant(),(UUID)row.get(idColumn)));
    }
    /** Fingerprints replay-relevant inquiry data with SHA-256. */
    private String hash(final String value) {try{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
}
