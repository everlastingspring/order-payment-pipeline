package com.paymentplatform.paymentservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot entry point for payment-service.
 *
 * <p>Payment-service handles the charge step in the saga. It consumes
 * {@code inventory.reserved} events, executes payments via the Strategy pattern
 * (currently {@code WalletPaymentProcessor}), and publishes {@code payment.completed}
 * or {@code payment.failed} events via the transactional outbox.</p>
 *
 * <p>Key safety mechanisms: Redis distributed lock + DB idempotency key table prevent
 * double-charging on retries. {@link EnableScheduling} activates the {@code OutboxProcessor}
 * poll cycle (default 5 s).</p>
 *
 * <p>Runs on port {@code 8082} (local) or as configured in the deployment environment.</p>
 */
@SpringBootApplication
@EnableScheduling
public class PaymentServiceApplication {

    /**
     * Application entry point. Bootstraps the Spring context and starts all listeners,
     * schedulers, and embedded Tomcat.
     *
     * @param args command-line arguments passed through to Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
