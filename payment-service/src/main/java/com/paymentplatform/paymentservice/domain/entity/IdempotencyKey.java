package com.paymentplatform.paymentservice.domain.entity;

import com.paymentplatform.commonlib.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_keys")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotencyKey {

    @Id
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String key;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "response_status", nullable = false)
    private PaymentStatus responseStatus;

    @Column(name = "response_body")
    private String responseBody;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = Instant.now();
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
