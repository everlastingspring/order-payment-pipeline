package com.paymentplatform.orderservice.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbox pattern record that guarantees at-least-once Kafka publication.
 *
 * <p>All domain events are first saved to this table within the same DB transaction
 * as the business change. A scheduled {@code OutboxProcessor} then reads PENDING
 * rows and publishes them to Kafka.</p>
 *
 * <p>This prevents the dual-write problem — without the outbox, a crash between
 * the DB commit and the Kafka send would lose the event permanently.</p>
 *
 * <p>Retry semantics: failed publications increment {@link #retryCount}. After
 * {@code MAX_RETRY_COUNT} failures the row is marked FAILED and skipped on
 * subsequent polls.</p>
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

    /** Domain aggregate type this event belongs to (e.g. "Order"). */
    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    /** UUID of the aggregate instance (e.g. order ID) — used as the Kafka message key. */
    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    /** Kafka topic name — mirrors the event type string (e.g. "order.created"). */
    @Column(name = "event_type", nullable = false)
    private String eventType;

    /** Kafka topic to publish to — identical to {@link #eventType} in this platform. */
    @Column(nullable = false)
    private String topic;

    /** Serialised JSON payload published verbatim as the Kafka message value. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    /**
     * Current publication state.
     * PENDING → PUBLISHED (success) or PENDING → FAILED (after max retries).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status;

    /** Immutable timestamp set at row creation. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Set when the Kafka send is confirmed. Null while PENDING. */
    @Column(name = "published_at")
    private Instant publishedAt;

    /** Number of failed publication attempts. Incremented by {@link #markFailed()}. */
    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (status == null) status = OutboxStatus.PENDING;
        createdAt = Instant.now();
        retryCount = 0;
    }

    /**
     * Marks this event as successfully published to Kafka.
     * Records the publication timestamp.
     */
    public void markPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = Instant.now();
    }

    /**
     * Records a failed publication attempt.
     * Increments {@link #retryCount} and sets status to FAILED.
     * The caller should check {@code retryCount >= MAX_RETRY_COUNT}
     * to decide whether to call {@link #retry()} or leave it as FAILED.
     */
    public void markFailed() {
        this.status = OutboxStatus.FAILED;
        this.retryCount++;
    }

    /**
     * Resets status to PENDING so the next outbox poll will retry publication.
     * Should only be called when {@link #retryCount} is below the max threshold.
     */
    public void retry() {
        this.status = OutboxStatus.PENDING;
    }
}
