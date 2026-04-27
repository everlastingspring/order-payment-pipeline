package com.paymentplatform.inventoryservice.domain.entity;

/**
 * Lifecycle states of a {@link ReservationRecord}.
 *
 * <p>Transitions:</p>
 * <ul>
 *   <li>{@code ACTIVE} — initial state; stock is locked for the order.</li>
 *   <li>{@code ACTIVE → RELEASED} — explicit saga compensation via {@code order.cancelled} or
 *       {@code order.failed (failedStep=PAYMENT)}.</li>
 *   <li>{@code ACTIVE → EXPIRED} — {@code ReservationExpiryProcessor} found the record
 *       older than {@code inventory.reservation.ttl-minutes} without a downstream event.</li>
 * </ul>
 *
 * <p>Only {@code ACTIVE} reservations are considered when releasing stock.
 * {@code RELEASED} and {@code EXPIRED} are terminal; they remain in the table for audit.</p>
 */
public enum ReservationStatus {

    /**
     * Stock is locked; the order is in-flight (PENDING → INVENTORY_RESERVED → payment).
     * The reservation record will transition out of this state on either saga completion
     * (order confirmed), saga compensation (order cancelled/failed), or TTL expiry.
     */
    ACTIVE,

    /**
     * Stock was returned to available via explicit saga compensation.
     * Set by {@code ReservationRecord.release()} when {@code order.cancelled} or
     * {@code order.failed} is received.
     */
    RELEASED,

    /**
     * TTL elapsed without the order completing. Set by {@code ReservationExpiryProcessor}.
     * Stock has been returned to available; an {@code inventory.failed} event is published
     * so the order-service transitions the stuck order to FAILED.
     */
    EXPIRED
}
