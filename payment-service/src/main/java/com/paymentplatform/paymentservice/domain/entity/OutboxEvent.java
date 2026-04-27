package com.paymentplatform.paymentservice.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Transactional outbox record for payment-service domain events.
 *
 * <p>Solves the dual-write problem: the payment charge ({@link Payment} update) and the Kafka
 * event ({@code payment.completed} / {@code payment.failed}) are both committed in the same
 * ACID database transaction. {@code OutboxProcessor} then polls {@code PENDING} rows and
 * publishes them asynchronously.</p>
 *
 * <p><strong>Events produced by payment-service:</strong></p>
 * <ul>
 *   <li>{@code payment.completed} — consumed by order-service (set CONFIRMED), notification-service,
 *       and wallet-service (debit ledger entry was already applied via synchronous REST call; this
 *       event confirms the full saga step).</li>
 *   <li>{@code payment.failed} — consumed by order-service (saga compensation → set FAILED),
 *       notification-service.</li>
 * </ul>
 *
 * <p><strong>Retry semantics:</strong> Up to 5 publish attempts. Permanently failed rows require
 * manual intervention to replay.</p>
 */
@Entity
@Table(name = "outbox_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {

    /** Primary key — UUID assigned at persist time. */
    @Id
    private UUID id;

    /**
     * Domain aggregate that produced this event — always {@code "Payment"} in this service.
     */
    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    /**
     * Identity of the aggregate — the {@code paymentId} (UUID string).
     * Used as the Kafka message key for partition routing.
     */
    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    /**
     * Fully-qualified event type — mirrors the Kafka topic constant.
     * Examples: {@code "payment.completed"}, {@code "payment.failed"}.
     */
    @Column(name = "event_type", nullable = false)
    private String eventType;

    /**
     * Kafka topic name (same value as {@link #eventType} in the current implementation).
     * Stored separately for forward compatibility.
     */
    @Column(nullable = false)
    private String topic;

    /**
     * JSON-serialised event payload from {@code ObjectMapper.writeValueAsString()}.
     * Stored as TEXT to handle payloads of arbitrary length.
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    /**
     * Current processing state.
     * {@code PENDING} → polled; {@code PUBLISHED} → Kafka confirmed; {@code FAILED} → exhausted retries.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status;

    /** Immutable creation timestamp. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * When Kafka confirmed delivery. Null while {@code PENDING} or {@code FAILED}.
     */
    @Column(name = "published_at")
    private Instant publishedAt;

    /**
     * Number of failed publish attempts.
     * Incremented by {@link #retry()}; triggers {@link #markFailed()} once it reaches {@code MAX_RETRIES}.
     */
    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = Instant.now();
    }

    /**
     * Transitions to {@code PUBLISHED} and records the delivery timestamp.
     */
    public void markPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = Instant.now();
    }

    /**
     * Transitions to {@code FAILED} (terminal) after exhausting all retries.
     */
    public void markFailed() {
        this.status = OutboxStatus.FAILED;
    }

    /**
     * Increments the retry counter. The event stays {@code PENDING} for the next poll cycle.
     */
    public void retry() {
        this.retryCount++;
    }
}
