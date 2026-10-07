package com.domain.payment;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
/** Bounds request bodies and uses database-backed write-rate limits across replicas. */
@Component @Order(Ordered.HIGHEST_PRECEDENCE+50)
@ConditionalOnProperty(name="app.bounds.enabled",havingValue="true")
public class RequestBounds extends OncePerRequestFilter {
    private static final int MAX_BYTES=65536;
    private final JdbcTemplate db;
    /** Shares durable rate buckets with the service database. */
    public RequestBounds(final JdbcTemplate db){this.db=db;}
    /** Rejects oversized chunked requests before JSON parsing and never trusts forwarded client IPs. */
    @Override protected void doFilterInternal(final HttpServletRequest request,final HttpServletResponse response,final FilterChain chain) throws ServletException,IOException {
        if(request.getRequestURI().startsWith("/internal/")||request.getRequestURI().startsWith("/actuator/")){chain.doFilter(request,response);return;}
        if(!request.getMethod().equals("GET")&&!request.getMethod().equals("HEAD")&&!request.getMethod().equals("OPTIONS")){
            final boolean login=request.getRequestURI().equals("/login");
            final String bucket=(login?"login:":"writes:")+request.getRemoteAddr();
            final Integer count;
            try{count=db.queryForObject("INSERT INTO request_buckets(bucket_key,window_start,request_count) VALUES(?,now(),1) ON CONFLICT(bucket_key) DO UPDATE SET request_count=CASE WHEN request_buckets.window_start<now()-interval '60 seconds' THEN 1 ELSE request_buckets.request_count+1 END,window_start=CASE WHEN request_buckets.window_start<now()-interval '60 seconds' THEN now() ELSE request_buckets.window_start END RETURNING request_count",Integer.class,bucket);}
            catch(Exception unavailable){deny(response,503,"Rate limiter unavailable");return;}
            if(count!=null&&count>(login?30:120)){response.setHeader("Retry-After","60");deny(response,429,"Too many requests");return;}
        }
        final String type=request.getContentType();
        if(type!=null&&type.toLowerCase(java.util.Locale.ROOT).contains("application/json")){
            final byte[] bytes=request.getInputStream().readNBytes(MAX_BYTES+1);
            if(bytes.length>MAX_BYTES){deny(response,413,"Request too large");return;}
            chain.doFilter(new HttpServletRequestWrapper(request){
                @Override public ServletInputStream getInputStream(){final var input=new ByteArrayInputStream(bytes);return new ServletInputStream(){public int read(){return input.read();}public boolean isFinished(){return input.available()==0;}public boolean isReady(){return true;}public void setReadListener(ReadListener listener){throw new UnsupportedOperationException("Synchronous body");}};}
                @Override public BufferedReader getReader(){return new BufferedReader(new InputStreamReader(getInputStream(),java.nio.charset.StandardCharsets.UTF_8));}
            },response);return;
        }
        if(request.getContentLengthLong()>MAX_BYTES){deny(response,413,"Request too large");return;}
        chain.doFilter(request,response);
    }
    /** Returns a fixed problem without reflecting user-controlled input. */
    private void deny(final HttpServletResponse response,final int status,final String detail) throws IOException {response.setStatus(status);response.setContentType("application/problem+json");response.getWriter().write("{\"status\":"+status+",\"detail\":\""+detail+"\"}");}
}
