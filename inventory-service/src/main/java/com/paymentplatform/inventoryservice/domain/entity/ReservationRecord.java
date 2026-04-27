package com.paymentplatform.inventoryservice.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Tracks a stock reservation made for a specific order item.
 *
 * <p>Created by {@code InventoryService.reserveInventory()} alongside the
 * {@link Inventory} quantity update. Used for saga compensation — when an
 * order is cancelled or fails, these records identify which stock to release.</p>
 *
 * <p>Also used by {@code ReservationExpiryProcessor} to find stale ACTIVE
 * reservations that should be released after the TTL expires.</p>
 */
@Entity
@Table(name = "reservation_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservationRecord {

    /** Primary key — UUID assigned at persist time. */
    @Id
    private UUID id;

    /** The order this reservation belongs to. */
    @Column(name = "order_id", nullable = false)
    private String orderId;

    /** The product whose stock was reserved. */
    @Column(name = "product_id", nullable = false)
    private UUID productId;

    /** The warehouse from which stock was reserved. */
    @Column(name = "warehouse_id", nullable = false)
    @Builder.Default
    private String warehouseId = "WH-001";

    /** Number of units reserved — used when releasing to restore exact quantity. */
    @Column(nullable = false)
    private int quantity;

    /**
     * Current state of the reservation.
     * ACTIVE → RELEASED (saga compensation) or EXPIRED (TTL exceeded).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ReservationStatus status = ReservationStatus.ACTIVE;

    /** Timestamp when the reservation was created. */
    @Column(name = "reserved_at", nullable = false)
    private Instant reservedAt;

    /** Timestamp when the reservation was released or expired. Null while ACTIVE. */
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
