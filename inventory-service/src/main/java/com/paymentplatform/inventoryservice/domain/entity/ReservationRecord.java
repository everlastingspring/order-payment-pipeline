package com.paymentplatform.inventoryservice.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reservation_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservationRecord {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "warehouse_id", nullable = false)
    @Builder.Default
    private String warehouseId = "WH-001";

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ReservationStatus status = ReservationStatus.ACTIVE;

    @Column(name = "reserved_at", nullable = false)
    private Instant reservedAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (reservedAt == null) reservedAt = Instant.now();
    }

    public void release() {
        this.status = ReservationStatus.RELEASED;
        this.releasedAt = Instant.now();
    }

    public void expire() {
        this.status = ReservationStatus.EXPIRED;
        this.releasedAt = Instant.now();
    }
}
