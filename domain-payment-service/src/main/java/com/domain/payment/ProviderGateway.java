package com.domain.payment;
import java.math.BigDecimal;
import java.util.*;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
/** Implements QPay Merchant V2 and a dev-only, explicitly labeled local test adapter. */
@Component
public class ProviderGateway {
    private final Transport http;
    private final Environment env;
    private final String provider,url,client,secret,code,callback;
    private String token;
    private long expiry;
    /** Keeps all provider credentials server-side. */
    public ProviderGateway(final Transport http,final Environment env,@Value("${app.provider}") final String provider,
            @Value("${app.qpay.url}") final String url,@Value("${app.qpay.client-id}") final String client,@Value("${app.qpay.secret}") final String secret,
            @Value("${app.qpay.invoice-code}") final String code,@Value("${app.qpay.callback-origin}") final String callback){
        this.http=http;this.env=env;this.provider=provider;this.url=url;this.client=client;this.secret=secret;this.code=code;this.callback=callback;
    }
    /** Returns only a configured provider, with local simulation restricted to the dev profile. */
    public String name(){
        if(provider.equals("local-test")&&env.matchesProfiles("dev"))return "LOCAL_TEST";
        if(provider.equals("qpay")&&!client.isBlank()&&!secret.isBlank()&&!code.isBlank()&&callback.startsWith("https://"))return "QPAY";
        throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"Payment provider is not configured");
    }
    /** Creates an invoice once using the order UUID as the merchant's unique reference. */
    public Map<String,Object> create(final UUID id,final UUID user,final BigDecimal amount,final String nonce){
        if(name().equals("LOCAL_TEST"))return Map.of("invoice_id",id.toString(),"test",true,"message","Туршилтын төлбөр — бодит мөнгө шилжихгүй.");
        return http.send(url+"/v2/invoice","POST",Map.of("invoice_code",code,"sender_invoice_no",id.toString(),"invoice_receiver_code",user.toString(),
                "invoice_description","Bemon "+id,"amount",amount,"callback_url",callback+"/v1/payments/callback/"+id+"?token="+nonce),"Authorization","Bearer "+access());
    }
    /** Confirms provider evidence after an authenticated callback hint, never from callback body claims. */
    public Receipt check(final String invoice){
        final var result=http.send(url+"/v2/payment/check","POST",Map.of("object_type","INVOICE","object_id",invoice,"offset",Map.of("page_number",1,"page_limit",100)),"Authorization","Bearer "+access());
        final var rows=result.get("rows");
        if(!(rows instanceof List<?> list))return null;
        BigDecimal total=BigDecimal.ZERO;final List<String> receipts=new ArrayList<>();
        for(final var raw:list){
            if(!(raw instanceof Map<?,?> row)||!"PAID".equals(row.get("payment_status")))continue;
            final String id=String.valueOf(row.get("payment_id"));
            final var detail=http.send(url+"/v2/payment/"+java.net.URLEncoder.encode(id,StandardCharsets.UTF_8),"GET",null,"Authorization","Bearer "+access());
            if(!"PAID".equals(detail.get("payment_status"))||!"MNT".equals(detail.get("payment_currency"))||!invoice.equals(detail.get("object_id")))
                throw new IllegalStateException("Incomplete provider evidence");
            total=total.add(new BigDecimal(String.valueOf(detail.get("payment_amount"))));receipts.add(id);
        }
        if(receipts.isEmpty())return null;
        if(new java.util.HashSet<>(receipts).size()!=receipts.size())throw new IllegalStateException("Duplicate provider evidence");
        Collections.sort(receipts);return new Receipt(String.join(",",receipts),total);
    }
    /** Cancels an unpaid invoice; uncertain outcomes remain explicitly unresolved. */
    public void cancel(final String invoice){if(!name().equals("LOCAL_TEST"))http.send(url+"/v2/invoice/"+java.net.URLEncoder.encode(invoice,StandardCharsets.UTF_8),"DELETE",null,"Authorization","Bearer "+access());}
    /** Requests a provider refund only for one verified card receipt. */
    public void refund(final String receipt){
        if(name().equals("LOCAL_TEST"))return;
        if(receipt.contains(","))throw new IllegalArgumentException("Multiple receipts require provider reconciliation");
        http.send(url+"/v2/payment/refund/"+java.net.URLEncoder.encode(receipt,StandardCharsets.UTF_8),"DELETE",Map.of("note","Bemon refund"),"Authorization","Bearer "+access());
        final var confirmed=http.send(url+"/v2/payment/"+java.net.URLEncoder.encode(receipt,StandardCharsets.UTF_8),"GET",null,"Authorization","Bearer "+access());
        if(!"REFUNDED".equals(confirmed.get("payment_status")))throw new IllegalStateException("Refund not confirmed by provider");
    }
    /** Caches merchant access tokens until their actual provider expiry. */
    private synchronized String access(){
        if(token!=null&&expiry>System.currentTimeMillis()+60000)return token;
        final var result=http.send(url+"/v2/auth/token","POST",null,"Authorization","Basic "+Base64.getEncoder().encodeToString((client+":"+secret).getBytes(StandardCharsets.UTF_8)));
        if(!(result.get("access_token") instanceof String value)||value.isBlank())throw new IllegalStateException("Invalid provider authentication");
        token=value;
        final long seconds=Long.parseLong(result.getOrDefault("expires_in",60).toString());
        final long absolute=Long.parseLong(result.getOrDefault("expires_at",0).toString());
        expiry=absolute>0?absolute*1000:seconds>1_000_000_000L?seconds*1000:System.currentTimeMillis()+seconds*1000;return token;
    }
    /** Verified receipt values are independent of caller-selected price or callback state. */
    public record Receipt(String id,BigDecimal amount){}
}
