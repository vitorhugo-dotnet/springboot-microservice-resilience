package com.vitorhugo.orders.observability;

import java.util.Map;
import java.util.concurrent.Executor;

import org.slf4j.MDC;

/**
 * Carrega o MDC da thread que submete para a thread que executa.
 *
 * <p>Sem isso o correlation id morre na fronteira do pool: a chamada ao
 * pagamento roda em outra thread, e os logs mais interessantes de um incidente
 * — os do retry, do timeout e do fallback — sairiam sem rastro nenhum.
 */
public class MdcPropagatingExecutor implements Executor {

    private final Executor delegate;

    public MdcPropagatingExecutor(Executor delegate) {
        this.delegate = delegate;
    }

    @Override
    public void execute(Runnable command) {
        Map<String, String> contexto = MDC.getCopyOfContextMap();

        delegate.execute(() -> {
            Map<String, String> anterior = MDC.getCopyOfContextMap();
            if (contexto != null) {
                MDC.setContextMap(contexto);
            } else {
                MDC.clear();
            }
            try {
                command.run();
            } finally {
                if (anterior != null) {
                    MDC.setContextMap(anterior);
                } else {
                    MDC.clear();
                }
            }
        });
    }
}
