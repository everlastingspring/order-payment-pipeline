package com.paymentplatform.notificationservice.infrastructure.sender;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Simulates sending notifications (email, SMS, push).
 *
 * In production, this would integrate with:
 *  - AWS SES / SendGrid for email
 *  - Twilio / AWS SNS for SMS
 *  - Firebase for push notifications
 *
 * Simulation: always succeeds. To test failure/DLQ flows,
 * send to recipient "fail@test.com".
 *
 * Resilience4j:
 *  - Circuit breaker: opens after 50% failure rate in 20-call sliding window
 *  - Retry: 3 attempts with exponential backoff for transient IO failures
 */
@Component
@Slf4j
public class NotificationSender {

    @CircuitBreaker(name = "notificationSender", fallbackMethod = "sendFallback")
    @Retry(name = "notificationSender")
    public boolean send(String recipientEmail, String subject, String body) {
        log.info("Sending notification to={}, subject={}", recipientEmail, subject);

        // Simulate processing time
        try {
            Thread.sleep(20 + (long) (Math.random() * 80));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Deterministic failure for testing
        if ("fail@test.com".equalsIgnoreCase(recipientEmail)) {
            log.warn("Simulated send failure for: {}", recipientEmail);
            return false;
        }

        log.info("Notification sent successfully to={}", recipientEmail);
        return true;
    }

    private boolean sendFallback(String recipientEmail, String subject, String body, Throwable t) {
        log.error("Notification sender circuit breaker fallback: to={}, error={}", recipientEmail, t.getMessage());
        return false;
    }
}
