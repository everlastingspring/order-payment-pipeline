package com.paymentplatform.orderservice.domain.entity;

/**
 * Publication state of an outbox event row.
 *
 * <p>The outbox processor polls for {@code PENDING} rows, publishes them,
 * and marks them {@code PUBLISHED}. Rows that exhaust retries are marked
 * {@code FAILED} and are no longer polled.</p>
 */
public enum OutboxStatus {

    /** Awaiting publication — picked up by the next outbox processor poll. */
    PENDING,

    /** Successfully published to Kafka — will not be reprocessed. */
    PUBLISHED,

    /**
     * Publication failed after all retry attempts.
     * Requires manual investigation or a separate reprocessing job.
     */
    FAILED
}
