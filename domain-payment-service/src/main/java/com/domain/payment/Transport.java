package com.domain.payment;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;
import org.springframework.stereotype.Component;
/** Bounded JSON transport whose errors never include request secrets or provider responses. */
@Component
public class Transport {
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper json;
    /** Supplies shared JSON serialization. */
    public Transport(final ObjectMapper json){this.json=json;}
    /** Sends one request; write retries must be controlled by durable application state. */
    public Map<String,Object> send(final String url,final String method,final Object body,final String header,final String value){
        try{
            final var request=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).header(header,value).header("Content-Type","application/json")
                    .method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
            final var response=client.send(request,HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()<200||response.statusCode()>=300)throw new IllegalStateException("Provider unavailable");
            if(response.body().isBlank())return Map.of();
            return json.readValue(response.body(),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
        }catch(Exception failure){if(failure instanceof InterruptedException)Thread.currentThread().interrupt();throw new IllegalStateException("Provider request not confirmed",failure);}
    }
}
