package com.paymentplatform.paymentservice.domain.entity;

import com.paymentplatform.commonlib.enums.PaymentMethod;
import com.paymentplatform.commonlib.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "transaction_reference")
    private String transactionReference;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "failure_code")
    private String failureCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

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

    public void markCompleted(String transactionReference) {
        this.status = PaymentStatus.COMPLETED;
        this.transactionReference = transactionReference;
    }

    public void markFailed(String failureReason, String failureCode) {
        this.status = PaymentStatus.FAILED;
        this.failureReason = failureReason;
        this.failureCode = failureCode;
    }
}
