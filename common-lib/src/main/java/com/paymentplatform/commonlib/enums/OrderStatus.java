package com.paymentplatform.commonlib.enums;

/**
 * Lifecycle states of an order in the choreography saga.
 *
 * <p>Typical happy-path progression:
 * {@code PENDING → INVENTORY_RESERVED → CONFIRMED}</p>
 *
 * <p>Only {@code CANCELLED} and {@code FAILED} are terminal states
 * that block further status transitions.
 * See {@code OrderService.cancelOrder()} for the cancellation guard logic.</p>
 */
public enum OrderStatus {

    /** Initial state — order persisted, outbox event pending publication. */
    PENDING,

    /** Stock locked by inventory-service; payment has not yet been attempted. */
    INVENTORY_RESERVED,

    /** Payment collected — saga complete. No further transitions expected. */
    CONFIRMED,

    /**
     * Enum value kept for backward compatibility with old documentation.
     * {@code CONFIRMED} is the actual happy-path terminal state in code.
     * This value is never set by any live code path.
     */
    COMPLETED,

    /**
     * Explicit user or system cancellation.
     * PENDING / INVENTORY_RESERVED / CONFIRMED orders can be cancelled.
     * Terminal — cannot be transitioned further.
     */
    CANCELLED,

    /**
     * Saga failure — either inventory was unavailable or payment was declined.
     * Terminal — cannot be transitioned further.
     */
    FAILED
}
