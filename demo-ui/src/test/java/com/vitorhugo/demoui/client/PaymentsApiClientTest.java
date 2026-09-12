package com.vitorhugo.demoui.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vitorhugo.payments.mode.PaymentMode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PaymentsApiClientTest {

    @Test
    void switchModePostsLowercaseModeAndReturnsPrettySuccessfulResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://payments.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PaymentsApiClient client = new PaymentsApiClient(builder.build(), new ObjectMapper(), "http://payments.test");
        server.expect(requestTo("http://payments.test/payments/mode/fail"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"mode\":\"FAIL\"}", MediaType.APPLICATION_JSON));

        ApiCallResult result = client.switchMode(PaymentMode.FAIL);

        assertThat(result.method()).isEqualTo("POST");
        assertThat(result.url()).isEqualTo("http://payments.test/payments/mode/fail");
        assertThat(result.status()).isEqualTo(200);
        assertThat(result.body()).contains("\"mode\" : \"FAIL\"");
        assertThat(result.successful()).isTrue();
        server.verify();
    }

    @Test
    void currentModePreservesHttpFailureStatusAndPrettyErrorBody() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://payments.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PaymentsApiClient client = new PaymentsApiClient(builder.build(), new ObjectMapper(), "http://payments.test");
        server.expect(requestTo("http://payments.test/payments/mode"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError().body("{\"error\":\"falhou\"}").contentType(MediaType.APPLICATION_JSON));

        ApiCallResult result = client.currentMode();

        assertThat(result.status()).isEqualTo(500);
        assertThat(result.body()).contains("\"error\" : \"falhou\"");
        assertThat(result.successful()).isFalse();
        server.verify();
    }

    @Test
    void currentModeMapsTransportFailureWithoutHttpStatus() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://payments.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PaymentsApiClient client = new PaymentsApiClient(builder.build(), new ObjectMapper(), "http://payments.test");
        server.expect(requestTo("http://payments.test/payments/mode"))
                .andRespond(request -> {
                    throw new ResourceAccessException("connection refused");
                });

        ApiCallResult result = client.currentMode();

        assertThat(result.status()).isNull();
        assertThat(result.friendlyMessage()).contains("connection refused");
        assertThat(result.successful()).isFalse();
        server.verify();
    }
}
