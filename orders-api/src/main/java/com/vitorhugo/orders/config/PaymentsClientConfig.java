package com.vitorhugo.orders.config;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class PaymentsClientConfig {

    /**
     * Rede de seguranca de ultimo recurso, deliberadamente MAIOR que o
     * TimeLimiter (1.5s). Quem corta a chamada e o TimeLimiter; este read timeout
     * so existe para que a thread do pool nao fique presa para sempre quando o
     * future ja foi abandonado — cancelar um {@code CompletableFuture} nao
     * interrompe o I/O bloqueante que esta rodando dentro dele.
     */
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

    private static final Duration CONNECT_TIMEOUT = Duration.ofMillis(500);

    /**
     * Maior que o {@code maxConcurrentCalls} do bulkhead (8) de proposito: quem
     * deve limitar a concorrencia e o bulkhead, com rejeicao explicita e
     * observavel, nao o tamanho do pool com enfileiramento silencioso.
     */
    private static final int POOL_SIZE = 16;

    @Bean
    RestClient paymentsRestClient(@Value("${payments.base-url}") String baseUrl) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                // O JDK HttpClient tenta HTTP/2 por padrao. O payments-api roda em
                // Tomcat sem h2c habilitado, entao a negociacao so adiciona uma
                // rodada de incerteza — e, contra servidores que aceitam h2c,
                // produz erros de stream dificeis de diagnosticar.
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Bean(destroyMethod = "shutdown")
    ExecutorService paymentsExecutor() {
        return Executors.newFixedThreadPool(POOL_SIZE, Thread.ofPlatform().name("payments-", 0).factory());
    }
}
