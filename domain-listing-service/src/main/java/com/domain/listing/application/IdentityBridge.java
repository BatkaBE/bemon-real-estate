package com.domain.listing.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Calls Identity through bounded, server-controlled URLs; never logs tokens or profile data. */
@Component
public class IdentityBridge {
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper json;
    private final String base;
    private final String key;
    /** Supplies the trusted internal endpoint and service credential. */
    public IdentityBridge(final ObjectMapper json,@Value("${app.identity-internal-url:http://localhost:9000}") final String base,
            @Value("${app.internal-key:}") final String key) {this.json=json;this.base=base;this.key=key;}
    /** Resolves contact details for the actual bearer-token principal. */
    public Map<String,Object> profile(final String token) {
        return exchange("/v1/users/me","GET",null,"Authorization","Bearer "+token);
    }
    /** Checks a revocation epoch without sharing service databases. */
    public long authVersion(final UUID userId) {
        return ((Number)exchange("/internal/accounts/"+userId+"/version","GET",null,"X-Internal-Key",key).get("version")).longValue();
    }
    /** Asks Identity to send an account-addressed notification. */
    public void notify(final UUID notificationId,final UUID userId,final String subject,final String body) {
        exchange("/internal/notifications","POST",Map.of("notificationId",notificationId,"userId",userId,"subject",subject,"body",body),"X-Internal-Key",key);
    }
    /** Restricts transport duration and converts provider failures into a safe service error. */
    private Map<String,Object> exchange(final String path,final String method,final Object body,final String header,final String value) {
        try {
            final var request=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(4))
                    .header(header,value).header("Content-Type","application/json")
                    .method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
            final var response=client.send(request,HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()<200||response.statusCode()>=300) throw new IllegalStateException("Identity unavailable");
            return json.readValue(response.body(),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
        } catch(Exception failure) { if(failure instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"Identity unavailable"); }
    }
}
