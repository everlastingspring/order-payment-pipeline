package com.paymentplatform.notificationservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot entry point for notification-service.
 *
 * <p>Notification-service is the platform's customer-communication hub.
 * It is a pure event consumer — it subscribes to 6 Kafka topics
 * ({@code order.created}, {@code order.completed}, {@code order.cancelled},
 * {@code order.failed}, {@code payment.completed}, {@code payment.failed})
 * and dispatches notifications via pluggable {@code NotificationChannel} strategies
 * (email, SMS). Failed deliveries are published to {@code notification.dlq}.</p>
 *
 * <p>{@link EnableScheduling} is present for any future scheduled cleanup tasks.</p>
 *
 * <p>Runs on port {@code 8084} (local) or as configured in the deployment environment.</p>
 */
@SpringBootApplication
@EnableScheduling
public class NotificationServiceApplication {

    /**
     * Application entry point. Bootstraps the Spring context and starts all listeners,
     * schedulers, and embedded Tomcat.
     *
     * @param args command-line arguments passed through to Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
