package com.vitorhugo.payments.authorization;

import java.util.Map;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payments")
public class PaymentAuthorizationController {

    private static final Logger log = LoggerFactory.getLogger(PaymentAuthorizationController.class);

    private final PaymentAuthorizationService service;

    public PaymentAuthorizationController(PaymentAuthorizationService service) {
        this.service = service;
    }

    @PostMapping("/authorize")
    public AuthorizationResponse authorize(@Valid @RequestBody AuthorizationRequest request) {
        return service.authorize(request);
    }

    @ExceptionHandler(PaymentFailureException.class)
    public ResponseEntity<Map<String, String>> handleFailure(PaymentFailureException e) {
        log.error("falha na autorizacao: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
    }
}
