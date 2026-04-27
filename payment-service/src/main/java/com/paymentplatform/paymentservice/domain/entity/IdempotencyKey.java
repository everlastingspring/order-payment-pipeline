package com.paymentplatform.paymentservice.domain.entity;

import com.paymentplatform.commonlib.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Idempotency record that prevents double-charging a customer for the same order.
 *
 * <p>After a payment attempt (success or failure), a row is saved here with
 * {@code key = "order:<orderId>"}. On subsequent Kafka redeliveries of the same
 * {@code inventory.reserved} event, {@code PaymentService} checks this table
 * first and returns early if a non-expired key exists.</p>
 *
 * <p>Rows expire after {@code payment.idempotency.ttl-seconds} (default 24h).
 * Expired rows are not deleted automatically — a future cleanup job can purge them.</p>
 */
@Entity
@Table(name = "idempotency_keys")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotencyKey {

    /** Primary key — UUID assigned at persist time. */
    @Id
    private UUID id;

    /**
     * The deduplication key — format {@code "order:<orderId>"}.
     * Unique constraint ensures only one record per order.
     */
    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String key;

    /** The payment record created during this idempotency window. */
    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    /** The outcome of the payment (COMPLETED or FAILED) — returned to duplicate callers. */
    @Enumerated(EnumType.STRING)
    @Column(name = "response_status", nullable = false)
    private PaymentStatus responseStatus;

    /** Serialised {@code PaymentDto} — the response body cached for duplicate requests. */
    @Column(name = "response_body")
    private String responseBody;

    /** Immutable creation timestamp. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Timestamp after which this record is considered expired and a new payment may be attempted. */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = Instant.now();
    }

    /**
     * Returns {@code true} if the TTL has elapsed and a new payment attempt is allowed.
     *
     * @return {@code true} when the current time is past {@link #expiresAt}
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
