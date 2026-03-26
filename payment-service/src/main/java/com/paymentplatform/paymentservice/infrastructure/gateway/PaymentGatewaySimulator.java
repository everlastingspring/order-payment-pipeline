package com.paymentplatform.paymentservice.infrastructure.gateway;

import com.paymentplatform.commonlib.enums.PaymentMethod;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Simulates an external payment gateway (Razorpay, Stripe, etc.).
 *
 * In production, this would be replaced with an actual gateway client
 * that calls the provider's API, handles 3DS challenges, tokenization, etc.
 *
 * Simulation rules:
 *  - Amounts ending in .99 → FAIL (for testing compensation flows)
 *  - Everything else       → SUCCESS with a generated txn reference
 *
 * Resilience4j:
 *  - Circuit breaker: opens after 50% failure rate in 10-call sliding window
 *  - Retry: 3 attempts with 500ms wait for transient IO/timeout failures
 */
@Component
@Slf4j
public class PaymentGatewaySimulator {

    @CircuitBreaker(name = "paymentGateway", fallbackMethod = "chargeFallback")
    @Retry(name = "paymentGateway")
    public GatewayResponse charge(String orderId, BigDecimal amount, String currency, PaymentMethod method) {
        // method param kept for future routing (e.g. different gateway per method)
        log.info("Gateway charging: orderId={}, amount={} {}, method={}", orderId, amount, currency, method);

        // Simulate processing delay (50-200ms)
        try {
            Thread.sleep(50 + (long) (Math.random() * 150));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Deterministic failure: amounts ending in .99 always fail
        if (amount.stripTrailingZeros().toPlainString().endsWith(".99")) {
            log.warn("Gateway declined payment: orderId={}, amount={}", orderId, amount);
            return GatewayResponse.failure("INSUFFICIENT_FUNDS", "Card declined — insufficient funds");
        }

        String txnRef = "TXN-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        log.info("Gateway approved payment: orderId={}, txnRef={}", orderId, txnRef);
        return GatewayResponse.success(txnRef);
    }

    private GatewayResponse chargeFallback(String orderId, BigDecimal amount, String currency,
                                            PaymentMethod method, Throwable t) {
        log.error("Payment gateway circuit breaker fallback triggered: orderId={}, error={}", orderId, t.getMessage());
        return GatewayResponse.failure("GATEWAY_UNAVAILABLE", "Payment gateway is temporarily unavailable");
    }

}

