package com.vitorhugo.demoui.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vitorhugo.payments.mode.PaymentMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Locale;

@Component
public class PaymentsApiClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    @Autowired
    public PaymentsApiClient(RestClient.Builder restClientBuilder,
                             ObjectMapper objectMapper,
                             @Value("${payments.base-url}") String baseUrl) {
        this(restClientBuilder.baseUrl(baseUrl).build(), objectMapper, baseUrl);
    }

    PaymentsApiClient(RestClient restClient, ObjectMapper objectMapper) {
        this(restClient, objectMapper, "");
    }

    PaymentsApiClient(RestClient restClient, ObjectMapper objectMapper, String baseUrl) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
    }

    public ApiCallResult currentMode() {
        return get("/payments/mode");
    }

    public ApiCallResult switchMode(PaymentMode mode) {
        return post("/payments/mode/" + mode.name().toLowerCase(Locale.ROOT), null);
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

    private ApiCallResult post(String path, Object payload) {
        try {
            RestClient.RequestBodySpec request = restClient.post().uri(path);
            if (payload != null) {
                request.body(payload);
            }
            return successful("POST", path, request.retrieve().toEntity(String.class));
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
}
