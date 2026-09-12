package com.vitorhugo.demoui.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class OrdersApiClientTest {

    @Test
    void resilienceStatusReturnsNullBodyUnchangedForNoContentResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://orders.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OrdersApiClient client = new OrdersApiClient(builder.build(), new ObjectMapper(), "http://orders.test");
        server.expect(requestTo("http://orders.test/resilience/status"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(org.springframework.http.HttpStatus.NO_CONTENT));

        ApiCallResult result = client.resilienceStatus();

        assertThat(result.status()).isEqualTo(204);
        assertThat(result.body()).isNull();
        assertThat(result.successful()).isTrue();
        server.verify();
    }

    @Test
    void createOrderPostsExpectedJsonAndReturnsCreatedResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://orders.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OrdersApiClient client = new OrdersApiClient(builder.build(), new ObjectMapper(), "http://orders.test");
        server.expect(requestTo("http://orders.test/orders"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"customerId\":\"c-1\",\"amount\":10.00}"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.CREATED)
                        .body("{\"id\":\"o-1\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        ApiCallResult result = client.createOrder("c-1", new BigDecimal("10.00"));

        assertThat(result.status()).isEqualTo(201);
        assertThat(result.body()).contains("\"id\" : \"o-1\"");
        assertThat(result.successful()).isTrue();
        server.verify();
    }

    @Test
    void resilienceStatusPreservesHttpFailureStatusAndBody() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://orders.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OrdersApiClient client = new OrdersApiClient(builder.build(), new ObjectMapper(), "http://orders.test");
        server.expect(requestTo("http://orders.test/resilience/status"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError().body("{\"error\":\"falhou\"}").contentType(MediaType.APPLICATION_JSON));

        ApiCallResult result = client.resilienceStatus();

        assertThat(result.status()).isEqualTo(500);
        assertThat(result.body()).contains("\"error\" : \"falhou\"");
        assertThat(result.successful()).isFalse();
        server.verify();
    }

    @Test
    void createOrderMapsTransportFailureToFriendlyUnavailableMessage() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://orders.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OrdersApiClient client = new OrdersApiClient(builder.build(), new ObjectMapper(), "http://orders.test");
        server.expect(requestTo("http://orders.test/orders"))
                .andRespond(request -> {
                    throw new ResourceAccessException("connection refused");
                });

        ApiCallResult result = client.createOrder("c-1", new BigDecimal("10.00"));

        assertThat(result.status()).isNull();
        assertThat(result.friendlyMessage()).contains("indisponivel");
        assertThat(result.friendlyMessage()).contains("connection refused");
        assertThat(result.successful()).isFalse();
        server.verify();
    }
}
