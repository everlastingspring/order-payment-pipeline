package com.paymentplatform.inventoryservice.domain.entity;

import com.paymentplatform.commonlib.enums.InventoryStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

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

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "available_quantity", nullable = false)
    private int availableQuantity;

    @Column(name = "reserved_quantity", nullable = false)
    @Builder.Default
    private int reservedQuantity = 0;

    @Column(name = "warehouse_id", nullable = false)
    @Builder.Default
    private String warehouseId = "WH-001";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private InventoryStatus status = InventoryStatus.AVAILABLE;

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
