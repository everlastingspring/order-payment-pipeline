package com.paymentplatform.orderservice.domain.entity;

import com.paymentplatform.commonlib.enums.OrderStatus;
import com.paymentplatform.commonlib.enums.PaymentMethod;
import com.paymentplatform.commonlib.enums.SagaStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA aggregate root representing a customer order.
 *
 * <p>This entity is the central state machine of the choreography saga.
 * Its {@link #status} and {@link #sagaStatus} fields are advanced by
 * {@link com.paymentplatform.orderservice.application.saga.OrderSagaManager}
 * as Kafka events arrive from inventory-service and payment-service.</p>
 *
 * <p>Design decisions:</p>
 * <ul>
 *   <li>{@link #paymentCompleted} and {@link #inventoryReserved} are boolean flags
 *       that drive the cancellation/refund logic without having to infer from status.</li>
 *   <li>{@link #sagaStatus} is a fine-grained debug field; {@link #status} is the
 *       coarse business field shown to the customer.</li>
 * </ul>
 */
@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    /** Primary key — UUID assigned at creation time. */
    @Id
    private UUID id;

    /** Business-level customer identifier (external user ID from the identity provider). */
    @Column(name = "customer_id", nullable = false)
    private String customerId;

    /** Customer's email — carried for use in Kafka events without extra lookups. */
    @Column(name = "customer_email", nullable = false)
    private String customerEmail;

    /**
     * Coarse-grained lifecycle state shown to the customer.
     * Advanced by {@code OrderSagaManager} in response to incoming Kafka events.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    /**
     * Fine-grained internal saga tracking state.
     * Used for debugging and observability; not exposed in API responses.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "saga_status", nullable = false)
    private SagaStatus sagaStatus;

    /**
     * Sum of all line-item subtotals.
     * Stored as {@code DECIMAL(19,2)} to prevent floating-point rounding on financial values.
     */
    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    /** ISO 4217 currency code (e.g. "INR"). Inherited from the first order item. */
    @Column(nullable = false, length = 3)
    private String currency;

    /** Payment method the customer selected at checkout. */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod;

    /** Delivery address — informational, not used for saga logic. */
    @Column(name = "shipping_address")
    private String shippingAddress;

    /**
     * UUID of the payment record created by payment-service.
     * Null until payment.completed is received.
     * Used to link the order to the payment record cross-service.
     */
    @Column(name = "payment_id")
    private String paymentId;

    /**
     * {@code true} once payment.completed has been processed.
     * Drives the refund decision in {@code cancelOrder()} — a CONFIRMED order
     * that is cancelled will have this flag set, triggering a wallet refund.
     */
    @Column(name = "payment_completed", nullable = false)
    private boolean paymentCompleted;

    /**
     * {@code true} once inventory.reserved has been processed.
     * Guards against processing inventory events out of order.
     */
    @Column(name = "inventory_reserved", nullable = false)
    private boolean inventoryReserved;

    /** Line items in this order. Cascade ALL + orphanRemoval keeps items in sync with the order. */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    /** Immutable creation timestamp set by {@link #onCreate()}. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Last-modified timestamp updated by {@link #onUpdate()} on every JPA save. */
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
     * Adds an item to the order and sets the back-reference on the item.
     * Must be used instead of direct list manipulation to maintain JPA consistency.
     *
     * @param item the item to add
     */
    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }
}
