package com.paymentplatform.orderservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot entry point for order-service.
 *
 * <p>Order-service is the saga orchestration hub of the payment platform.
 * It manages the order lifecycle ({@code PENDING → INVENTORY_RESERVED → CONFIRMED / FAILED})
 * via choreography events over Kafka.</p>
 *
 * <p>{@link EnableScheduling} activates the {@code OutboxProcessor} poll cycle that
 * publishes PENDING outbox events to Kafka every 5 seconds (configurable via
 * {@code outbox.processor.poll-interval-ms}).</p>
 *
 * <p>Runs on port {@code 8081} (local) or as configured in the deployment environment.</p>
 */
@SpringBootApplication
@EnableScheduling
public class OrderServiceApplication {

    /**
     * Application entry point. Bootstraps the Spring context and starts all listeners,
     * schedulers, and embedded Tomcat.
     *
     * @param args command-line arguments passed through to Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
