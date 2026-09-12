package com.vitorhugo.demoui.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;

@Component
public class OrdersApiClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    @Autowired
    public OrdersApiClient(RestClient.Builder restClientBuilder,
                           ObjectMapper objectMapper,
                           @Value("${orders.base-url}") String baseUrl) {
        this(restClientBuilder.baseUrl(baseUrl).build(), objectMapper, baseUrl);
    }

    OrdersApiClient(RestClient restClient, ObjectMapper objectMapper) {
        this(restClient, objectMapper, "");
    }

    OrdersApiClient(RestClient restClient, ObjectMapper objectMapper, String baseUrl) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
    }

    public ApiCallResult createOrder(String customerId, BigDecimal amount) {
        return post("/orders", new CreateOrderPayload(customerId, amount));
    }

    public ApiCallResult resilienceStatus() {
        return get("/resilience/status");
    }

    private ApiCallResult get(String path) {
        try {
            return successful("GET", path, restClient.get().uri(path).retrieve().toEntity(String.class));
        } catch (RestClientResponseException exception) {
            return httpFailure("GET", path, exception);
        } catch (ResourceAccessException exception) {
            return transportFailure("GET", path, exception);
        }
    }

    private ApiCallResult post(String path, CreateOrderPayload payload) {
        try {
            ResponseEntity<String> response = restClient.post().uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toEntity(String.class);
            return successful("POST", path, response);
        } catch (RestClientResponseException exception) {
            return httpFailure("POST", path, exception);
        } catch (ResourceAccessException exception) {
            return transportFailure("POST", path, exception);
        }
    }

    private ApiCallResult successful(String method, String path, ResponseEntity<String> response) {
        return new ApiCallResult(method, url(path), response.getStatusCode().value(), prettyPrint(response.getBody()),
                "Chamada concluida com sucesso.", true);
    }

    private ApiCallResult httpFailure(String method, String path, RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        return new ApiCallResult(method, url(path), status, prettyPrint(exception.getResponseBodyAsString()),
                "A API respondeu HTTP " + status + ".", false);
    }

    private ApiCallResult transportFailure(String method, String path, ResourceAccessException exception) {
        return new ApiCallResult(method, url(path), null, null,
                "Servico indisponivel: " + safeMessage(exception), false);
    }

    private String prettyPrint(String body) {
        if (body == null || body.isBlank()) {
            return body;
        }
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectMapper.readTree(body));
        } catch (JsonProcessingException exception) {
            return body;
        }
    }

    private String safeMessage(ResourceAccessException exception) {
        Throwable cause = exception.getCause();
        return cause != null && cause.getMessage() != null ? cause.getMessage() : exception.getMessage();
    }

    private String url(String path) {
        return baseUrl + path;
    }

    private record CreateOrderPayload(String customerId, BigDecimal amount) {
    }
}
