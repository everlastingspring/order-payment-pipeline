package com.paymentplatform.inventoryservice.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a sellable product in inventory_db.
 *
 * <p>Product metadata (name, price) is denormalised here for display in API responses.
 * The authoritative product catalogue lives in inventory-service only — other services
 * carry the product name and SKU in their domain events ({@code OrderItemDto}) to avoid
 * cross-service lookups at query time.</p>
 *
 * <p>Seeded by Flyway migration {@code V2__seed_products.sql} with 5 test products.</p>
 */
@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    /** Primary key — UUID defined in Flyway seed so it is deterministic across environments. */
    @Id
    private UUID id;

    /** Display name (e.g. "Wireless Mouse"). */
    @Column(nullable = false)
    private String name;

    /**
     * Unique stock-keeping unit code (e.g. "MOUSE-WL-001").
     * Unique constraint prevents duplicate SKUs across products.
     */
    @Column(nullable = false, unique = true)
    private String sku;

    /** Optional longer description. May be null. */
    private String description;

    /** Standard selling price. Stored as {@code DECIMAL(19,2)} for precision. */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    /** ISO 4217 currency code for the price. */
    @Column(nullable = false, length = 3)
    private String currency;

    /**
     * Whether this product can be ordered.
     * Soft-delete alternative — set to {@code false} to hide without removing history.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

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
}
