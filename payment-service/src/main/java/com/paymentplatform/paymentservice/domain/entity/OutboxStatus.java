package com.paymentplatform.paymentservice.domain.entity;

/**
 * Processing state of an {@link OutboxEvent} record in the payment-service outbox.
 *
 * <p>The {@code OutboxProcessor} polls for {@code PENDING} rows and attempts to publish
 * them to Kafka. Terminal states are {@code PUBLISHED} and {@code FAILED}.</p>
 *
 * <p>Transitions:</p>
 * <pre>
 *   PENDING ──(Kafka send OK)──► PUBLISHED
 *   PENDING ──(retryCount &ge; 5)──► FAILED
 * </pre>
 */
public enum OutboxStatus {

    /**
     * Written inside the business transaction by {@code PaymentService.saveOutboxEvent()}.
     * Picked up by {@code OutboxProcessor} on the next scheduled poll (default every 5 s).
     */
    PENDING,

    /**
     * Kafka broker accepted the message. Downstream services (order-service, notification-service,
     * wallet-service) will eventually consume the event regardless of their availability at publish time.
     */
    PUBLISHED,

    /**
     * Kafka publish failed after {@code MAX_RETRIES} (5) attempts.
     * The corresponding payment has already been charged or failed — this is a publish-side failure only.
     * Manual intervention is required to replay the event.
     */
    FAILED
}
