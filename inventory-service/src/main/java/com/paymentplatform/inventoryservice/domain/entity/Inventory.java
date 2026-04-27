package com.paymentplatform.inventoryservice.domain.entity;

import com.paymentplatform.commonlib.enums.InventoryStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing the stock levels for a product in a specific warehouse.
 *
 * <p>Unique constraint on {@code (product_id, warehouse_id)} — one row per
 * product-warehouse combination.</p>
 *
 * <p>Quantity semantics:</p>
 * <ul>
 *   <li>{@link #availableQuantity}: units that can be ordered right now.</li>
 *   <li>{@link #reservedQuantity}: units locked for active orders, not yet fulfilled.</li>
 * </ul>
 *
 * <p>Total stock = {@code availableQuantity + reservedQuantity}.
 * {@link #status} is derived from these two values by {@code updateStatus()}.</p>
 */
@Entity
@Table(name = "inventory", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"product_id", "warehouse_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inventory {

    /** Primary key — UUID assigned at persist time. */
    @Id
    private UUID id;

    /**
     * The product this inventory row tracks.
     * LAZY fetch — not needed in most queries; use {@code findByProductIdWithProduct} when required.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /**
     * Units available for new orders.
     * Decreased by {@link #reserve(int)} and increased by {@link #release(int)} / {@link #restock(int)}.
     */
    @Column(name = "available_quantity", nullable = false)
    private int availableQuantity;

    /**
     * Units currently locked for active orders.
     * Increased by {@link #reserve(int)}, decreased by {@link #release(int)}.
     */
    @Column(name = "reserved_quantity", nullable = false)
    @Builder.Default
    private int reservedQuantity = 0;

    /** Warehouse where this stock is held. Default "WH-001" for single-warehouse mode. */
    @Column(name = "warehouse_id", nullable = false)
    @Builder.Default
    private String warehouseId = "WH-001";

    /**
     * Derived availability status — recomputed by {@code updateStatus()} after every quantity change.
     * See {@link com.paymentplatform.commonlib.enums.InventoryStatus} for values.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private InventoryStatus status = InventoryStatus.AVAILABLE;

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
     * Reserves the requested quantity if enough stock is available.
     * @return true if reservation succeeded
     */
    public boolean reserve(int quantity) {
        if (availableQuantity < quantity) {
            return false;
        }
        availableQuantity -= quantity;
        reservedQuantity += quantity;
        updateStatus();
        return true;
    }

    /**
     * Adds stock to available quantity (admin restock operation).
     * Always safe to call — no preconditions.
     */
    public void restock(int quantity) {
        this.availableQuantity += quantity;
        updateStatus();
    }

    /**
     * Releases previously reserved quantity back to available stock.
     * Used for saga compensation when an order is cancelled.
     */
    public void release(int quantity) {
        int toRelease = Math.min(quantity, reservedQuantity);
        reservedQuantity -= toRelease;
        availableQuantity += toRelease;
        updateStatus();
    }

    private void updateStatus() {
        if (availableQuantity == 0 && reservedQuantity == 0) {
            status = InventoryStatus.OUT_OF_STOCK;
        } else if (availableQuantity == 0) {
            status = InventoryStatus.DEPLETED;
        } else {
            status = InventoryStatus.AVAILABLE;
        }
    }
}
