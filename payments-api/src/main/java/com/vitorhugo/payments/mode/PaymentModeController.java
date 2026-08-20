package com.vitorhugo.payments.mode;

import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payments/mode")
public class PaymentModeController {

    private static final Logger log = LoggerFactory.getLogger(PaymentModeController.class);

    private final PaymentModeHolder holder;

    public PaymentModeController(PaymentModeHolder holder) {
        this.holder = holder;
    }

    @GetMapping
    public Map<String, String> current() {
        return Map.of("mode", holder.current().name());
    }

    @PostMapping("/{mode}")
    public Map<String, String> switchTo(@PathVariable String mode) {
        PaymentMode novo = parse(mode);
        PaymentMode anterior = holder.current();
        holder.set(novo);
        log.warn("modo de pagamento alterado de {} para {}", anterior, novo);
        return Map.of("mode", novo.name());
    }

    private PaymentMode parse(String mode) {
        try {
            return PaymentMode.valueOf(mode.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new UnknownPaymentModeException(mode);
        }
    }

    @ExceptionHandler(UnknownPaymentModeException.class)
    public ResponseEntity<Map<String, String>> handleUnknownMode(UnknownPaymentModeException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", e.getMessage()));
    }

    static class UnknownPaymentModeException extends RuntimeException {

        UnknownPaymentModeException(String mode) {
            super("modo desconhecido: '%s'. Use um de: SUCCESS, SLOW, FAIL, FLAKY, DECLINED"
                    .formatted(mode));
        }
    }
}
