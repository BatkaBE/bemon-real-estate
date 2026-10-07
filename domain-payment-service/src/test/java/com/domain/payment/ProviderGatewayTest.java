package com.domain.payment;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.env.MockEnvironment;
/** Checks the provider contract and fail-closed settlement without contacting a merchant account. */
class ProviderGatewayTest {
    private Transport http;
    private ProviderGateway gateway;
    /** Builds a deterministic merchant transport fixture. */
    @BeforeEach void setup(){http=mock(Transport.class);gateway=new ProviderGateway(http,new MockEnvironment(),"qpay","https://merchant.test","client","secret","INVOICE","https://gerhub.test");
        when(http.send(endsWith("/v2/auth/token"),any(),any(),any(),any())).thenReturn(Map.of("access_token","provider-token","expires_in",3600));}
    /** Invoice writes use a stable order reference and cache the provider token. */
    @Test void uniqueReferenceAndTokenCache(){when(http.send(endsWith("/v2/invoice"),any(),any(),any(),any())).thenReturn(Map.of("invoice_id","invoice"));UUID id=UUID.randomUUID();gateway.create(id,UUID.randomUUID(),new BigDecimal("20000"),"nonce");gateway.create(UUID.randomUUID(),UUID.randomUUID(),new BigDecimal("20000"),"nonce");
        verify(http,times(1)).send(endsWith("/v2/auth/token"),any(),any(),any(),any());verify(http).send(eq("https://merchant.test/v2/invoice"),eq("POST"),argThat(body->body instanceof Map<?,?> m&&id.toString().equals(m.get("sender_invoice_no"))&&m.get("callback_url").toString().contains("?token=nonce")),eq("Authorization"),eq("Bearer provider-token"));}
    /** Signed callbacks still cannot settle a different invoice or denomination. */
    @Test void rejectsWrongInvoiceAndCurrency(){when(http.send(endsWith("/v2/payment/check"),any(),any(),any(),any())).thenReturn(Map.of("rows",List.of(Map.of("payment_status","PAID","payment_id","receipt"))));
        when(http.send(endsWith("/v2/payment/receipt"),any(),any(),any(),any())).thenReturn(Map.of("payment_status","PAID","payment_currency","AUD","payment_amount","20000","object_id","other"));
        assertThatThrownBy(()->gateway.check("invoice")).isInstanceOf(IllegalStateException.class);}
    /** Empty provider evidence never becomes a paid receipt. */
    @Test void unpaidIsNotPaid(){when(http.send(endsWith("/v2/payment/check"),any(),any(),any(),any())).thenReturn(Map.of("rows",List.of()));assertThat(gateway.check("invoice")).isNull();}
    /** Local simulation is unavailable outside an explicit development profile. */
    @Test void simulationRequiresDev(){assertThatThrownBy(()->new ProviderGateway(http,new MockEnvironment(),"local-test","","","","","").name()).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);}
}
