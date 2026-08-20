package com.vitorhugo.orders.payments;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Unico ponto do orders-api que fala com o payments-api — e, por consequencia,
 * o unico lugar onde as politicas de resiliencia precisam existir.
 *
 * <p>A ordem em que os aspectos do Resilience4j sao aplicados e fixa e vale a
 * pena ter em mente ao ler este arquivo:
 *
 * <pre>Retry ( CircuitBreaker ( TimeLimiter ( Bulkhead ( chamada ) ) ) )</pre>
 *
 * <p>O {@code fallbackMethod} fica no {@code @Retry} porque ele e o aspecto mais
 * externo. Se estivesse no {@code @CircuitBreaker}, o fallback devolveria um
 * valor "de sucesso" antes do retry enxergar a falha, e o retry nunca retentaria.
 */
@Component
public class PaymentsClient {

    private static final Logger log = LoggerFactory.getLogger(PaymentsClient.class);

    /** Nome das instancias no application.yml. Uma politica por nome, todas iguais. */
    private static final String INSTANCIA = "payments";

    private final RestClient restClient;
    private final Executor executor;

    public PaymentsClient(RestClient paymentsRestClient,
                          @Qualifier("paymentsExecutor") Executor paymentsExecutor) {
        this.restClient = paymentsRestClient;
        this.executor = paymentsExecutor;
    }

    @Bulkhead(name = INSTANCIA, type = Bulkhead.Type.SEMAPHORE)
    @TimeLimiter(name = INSTANCIA)
    @CircuitBreaker(name = INSTANCIA)
    @Retry(name = INSTANCIA, fallbackMethod = "indisponivel")
    public CompletableFuture<PaymentAuthorization> authorize(PaymentAuthorizationRequest request) {
        return CompletableFuture.supplyAsync(() -> chamar(request), executor);
    }

    private PaymentAuthorization chamar(PaymentAuthorizationRequest request) {
        return restClient.post()
                .uri("/payments/authorize")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AuthorizePayload(request.orderId(), request.amount()))
                .exchange((req, response) -> {
                    if (response.getStatusCode().is2xxSuccessful()) {
                        AuthorizedPayload body = response.bodyTo(AuthorizedPayload.class);
                        return new PaymentAuthorization.Authorized(body.authorizationId());
                    }
                    if (response.getStatusCode().value() == 422) {
                        // Recusa de negocio: a dependencia esta saudavel. Volta como
                        // valor normal, entao o circuit breaker registra sucesso e o
                        // retry nao insiste — insistir numa recusa nao muda nada.
                        return declinada(response);
                    }
                    throw new PaymentsUnavailableException(
                            "payments-api respondeu " + response.getStatusCode().value());
                });
    }

    /**
     * O motivo da recusa e informativo; o status 422 e que carrega a decisao. Um
     * corpo que nao da para ler nao pode transformar uma recusa em
     * indisponibilidade — isso mandaria o pedido para a fila de pendentes por
     * engano, e alguem tentaria autorizar de novo algo que ja foi negado.
     */
    private PaymentAuthorization declinada(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response) {
        String motivo = "recusado pelo pagamento";
        try {
            DeclinedPayload body = response.bodyTo(DeclinedPayload.class);
            if (body != null && body.reason() != null) {
                motivo = body.reason();
            }
        } catch (RuntimeException e) {
            log.debug("corpo da recusa ilegivel, usando motivo generico", e);
        }
        return new PaymentAuthorization.Declined(motivo);
    }

    /**
     * Acionado quando o retry se esgota, o circuito esta aberto ou o bulkhead
     * esta cheio. Traduz a causa tecnica num motivo legivel — o cliente do
     * orders-api recebe uma resposta controlada, nunca um 500 generico.
     */
    @SuppressWarnings("unused")
    private CompletableFuture<PaymentAuthorization> indisponivel(
            PaymentAuthorizationRequest request, Throwable causa) {

        Throwable raiz = desembrulhar(causa);
        String motivo = motivo(raiz);
        log.warn("autorizacao indisponivel para o pedido {}: {} ({})",
                request.orderId(), motivo, raiz.getClass().getSimpleName());

        return CompletableFuture.completedFuture(new PaymentAuthorization.Unavailable(motivo));
    }

    private String motivo(Throwable raiz) {
        return switch (raiz) {
            case CallNotPermittedException ignored -> "circuito aberto: chamada nem foi tentada";
            case BulkheadFullException ignored -> "bulkhead cheio: chamadas simultaneas no limite";
            case java.util.concurrent.TimeoutException ignored -> "timeout na chamada ao pagamento";
            default -> "falha ao chamar o pagamento: " + raiz.getMessage();
        };
    }

    private Throwable desembrulhar(Throwable causa) {
        return causa instanceof CompletionException && causa.getCause() != null
                ? causa.getCause()
                : causa;
    }

    /** Falha da dependencia. Contada pelo circuit breaker e retentada pelo retry. */
    static class PaymentsUnavailableException extends RuntimeException {

        PaymentsUnavailableException(String message) {
            super(message);
        }
    }

    private record AuthorizePayload(String orderId, BigDecimal amount) {
    }

    private record AuthorizedPayload(String authorizationId, String orderId, BigDecimal amount, String status) {
    }

    private record DeclinedPayload(String reason) {
    }
}
