package com.paymentplatform.orderservice.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a single line item within an {@link Order}.
 *
 * <p>Items are owned by the order ({@code CascadeType.ALL, orphanRemoval=true})
 * and share its lifecycle. They are created once at order-creation time and
 * never mutated thereafter.</p>
 */
@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

    /** Primary key — UUID assigned at persist time via {@code @PrePersist}. */
    @Id
    private UUID id;

    /**
     * The owning order.
     * LAZY fetch — not loaded unless explicitly accessed to avoid N+1 in list queries.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /** UUID of the product in inventory_db (denormalised for event building). */
    @Column(name = "product_id", nullable = false)
    private String productId;

    /** Display name at time of purchase — stored to preserve history even if the product name changes. */
    @Column(name = "product_name")
    private String productName;

    /** Stock-keeping unit code at time of purchase. */
    private String sku;

    /** Number of units ordered. */
    @Column(nullable = false)
    private int quantity;

    /** Price per unit at time of purchase — stored to lock in the price. */
    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    /** ISO 4217 currency code. */
    @Column(nullable = false, length = 3)
    private String currency;

    /**
     * Pre-computed {@code unitPrice × quantity}.
     * Calculated in {@link #onCreate()} to avoid recomputing on every read.
     */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotal;

    /** Immutable creation timestamp. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = Instant.now();
        subtotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
