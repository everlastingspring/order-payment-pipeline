package com.paymentplatform.inventoryservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot entry point for inventory-service.
 *
 * <p>Inventory-service manages stock reservations in the saga. It consumes
 * {@code order.created} events, performs two-pass atomic reservation of all line items,
 * and publishes {@code inventory.reserved} or {@code inventory.failed} via the outbox.
 * It also handles saga compensation on {@code order.cancelled} and {@code order.failed}.</p>
 *
 * <p>{@link EnableScheduling} activates two schedulers:
 * the {@code OutboxProcessor} poll cycle (default 5 s) and the
 * {@code ReservationExpiryProcessor} TTL reaper (default every 60 s).</p>
 *
 * <p>Runs on port {@code 8083} (local) or as configured in the deployment environment.</p>
 */
@SpringBootApplication
@EnableScheduling
public class InventoryServiceApplication {

    /**
     * Application entry point. Bootstraps the Spring context and starts all listeners,
     * schedulers, and embedded Tomcat.
     *
     * @param args command-line arguments passed through to Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }
}
