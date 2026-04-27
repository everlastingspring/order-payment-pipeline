package com.paymentplatform.paymentservice.domain.entity;

import com.paymentplatform.commonlib.enums.PaymentMethod;
import com.paymentplatform.commonlib.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a single payment attempt in payment_db.
 *
 * <p>Created by {@code PaymentService.executePayment()} when the
 * {@code inventory.reserved} event is consumed. The status transitions
 * from {@code PROCESSING} to {@code COMPLETED} or {@code FAILED}
 * within the same transaction.</p>
 *
 * <p>The {@link #idempotencyKey} ties this payment record to the
 * {@link IdempotencyKey} row, enabling duplicate detection across
 * service restarts and Kafka redeliveries.</p>
 */
@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    /** Primary key — UUID assigned at persist time. */
    @Id
    private UUID id;

    /** The order this payment covers. Foreign key from order_db (cross-service link, not a JPA @ManyToOne). */
    @Column(name = "order_id", nullable = false)
    private String orderId;

    /** The customer being charged. */
    @Column(name = "customer_id", nullable = false)
    private String customerId;

    /**
     * Current payment lifecycle state.
     * Starts as PROCESSING; transitions to COMPLETED or FAILED within the same DB transaction.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    /**
     * Amount charged.
     * {@code DECIMAL(19,2)} chosen to avoid floating-point rounding on financial values.
     */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    /** ISO 4217 currency code. */
    @Column(nullable = false, length = 3)
    private String currency;

    /** Payment method used for this charge. */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod;

    /**
     * The idempotency key used to deduplicate this payment.
     * Format: {@code "order:<orderId>"}.
     * Stored on the Payment record to link it back to the IdempotencyKey table.
     */
    @Column(name = "idempotency_key")
    private String idempotencyKey;

    /**
     * Opaque reference returned by the payment processor.
     * For WALLET: {@code "WALLET-XXXXXXXXXXXX"}. For gateways: provider-assigned ID.
     * Null if the payment failed.
     */
    @Column(name = "transaction_reference")
    private String transactionReference;

    /** Human-readable failure message. Null on success. */
    @Column(name = "failure_reason")
    private String failureReason;

    /**
     * Machine-readable failure code from the processor.
     * Examples: {@code "INSUFFICIENT_WALLET_BALANCE"}, {@code "GATEWAY_UNAVAILABLE"}.
     * Null on success.
     */
    @Column(name = "failure_code")
    private String failureCode;

    /** Immutable creation timestamp. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Last-updated timestamp. */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Marks the payment as successfully completed and records the processor's transaction reference.
     *
     * @param transactionReference opaque reference from the payment processor
     */
    public void markCompleted(String transactionReference) {
        this.status = PaymentStatus.COMPLETED;
        this.transactionReference = transactionReference;
    }

    /**
     * Marks the payment as failed and records the failure details.
     *
     * @param failureReason human-readable failure message
     * @param failureCode   machine-readable failure code from the processor
     */
    public void markFailed(String failureReason, String failureCode) {
        this.status = PaymentStatus.FAILED;
        this.failureReason = failureReason;
        this.failureCode = failureCode;
    }
}
