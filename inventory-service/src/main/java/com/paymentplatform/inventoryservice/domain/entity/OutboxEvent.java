package com.paymentplatform.inventoryservice.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Transactional outbox record — solves the dual-write problem between the database
 * and Kafka for inventory domain events.
 *
 * <p><strong>Dual-write problem:</strong> Without the outbox, inventory-service would need to
 * update stock levels AND publish to Kafka in the same operation. If the service crashes
 * between the two, the event is silently lost. With the outbox, both the stock mutation
 * and the outbox row are written in a single ACID transaction. {@code OutboxProcessor}
 * then polls {@code PENDING} rows and publishes them to Kafka asynchronously.</p>
 *
 * <p><strong>Retry semantics:</strong> Up to 5 publish attempts are made. On the 5th failure
 * the row is marked {@code FAILED} and requires manual intervention. Successful publish
 * sets status to {@code PUBLISHED} and records {@link #publishedAt}.</p>
 *
 * <p><strong>Events produced by inventory-service:</strong></p>
 * <ul>
 *   <li>{@code inventory.reserved} — all items reserved, payment-service should proceed.</li>
 *   <li>{@code inventory.failed} — one or more items unavailable or TTL expired.</li>
 * </ul>
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
     * Domain aggregate that produced this event — always {@code "Inventory"} in this service.
     * Useful for filtering / debugging multi-aggregate outbox tables.
     */
    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    /**
     * Identity of the aggregate that produced the event — typically the {@code orderId}
     * since inventory events are always in the context of an order.
     */
    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    /**
     * Fully-qualified event type string — mirrors the Kafka topic name for traceability.
     * Examples: {@code "inventory.reserved"}, {@code "inventory.failed"}.
     */
    @Column(name = "event_type", nullable = false)
    private String eventType;

    /**
     * Kafka topic to publish to — e.g. {@code "inventory.reserved"}.
     * Stored explicitly so {@code OutboxProcessor} does not need to infer it.
     */
    @Column(nullable = false)
    private String topic;

    /**
     * JSON-serialised event payload written by {@code ObjectMapper.writeValueAsString()}.
     * Stored as TEXT to handle arbitrary event sizes without truncation.
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    /**
     * Current processing state.
     * {@code PENDING} → polled by {@code OutboxProcessor};
     * {@code PUBLISHED} → Kafka accepted; {@code FAILED} → exhausted retries.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status;

    /** Immutable creation timestamp — set once in {@link #onCreate()}. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Timestamp when the Kafka broker confirmed receipt.
     * Null while {@code PENDING} or {@code FAILED}.
     */
    @Column(name = "published_at")
    private Instant publishedAt;

    /**
     * Number of failed Kafka publish attempts.
     * Incremented by {@link #retry()}; once it reaches {@code MAX_RETRIES} (5)
     * {@link #markFailed()} is called.
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
     * Transitions this event to {@code PUBLISHED} and records the publish timestamp.
     * Called by {@code OutboxProcessor} after {@code KafkaTemplate.send()} resolves successfully.
     */
    public void markPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = Instant.now();
    }

    /**
     * Transitions this event to {@code FAILED} (terminal).
     * Called after {@link #retryCount} reaches {@code MAX_RETRIES}.
     */
    public void markFailed() {
        this.status = OutboxStatus.FAILED;
    }

    /**
     * Increments the retry counter without changing status.
     * The event remains {@code PENDING} and will be retried on the next poll cycle
     * until {@code MAX_RETRIES} is reached.
     */
    public void retry() {
        this.retryCount++;
    }
}
