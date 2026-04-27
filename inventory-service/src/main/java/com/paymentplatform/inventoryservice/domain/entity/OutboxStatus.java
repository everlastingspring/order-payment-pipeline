package com.paymentplatform.inventoryservice.domain.entity;

/**
 * Processing state of an {@link OutboxEvent} record.
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
     * Written by a service during its business transaction.
     * Picked up by {@code OutboxProcessor} on the next scheduled poll (every 5 s by default).
     */
    PENDING,

    /**
     * Kafka broker accepted the message. The event is durable — consumers will eventually
     * receive it regardless of downstream availability at publish time.
     */
    PUBLISHED,

    /**
     * Kafka publish failed after {@code MAX_RETRIES} (5) attempts.
     * Requires manual intervention or a separate dead-letter sweep. The domain transaction
     * that created this event has already committed; compensating action may be needed.
     */
    FAILED
}
