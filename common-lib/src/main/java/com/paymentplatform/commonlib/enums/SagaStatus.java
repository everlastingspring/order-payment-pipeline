package com.paymentplatform.commonlib.enums;

/**
 * Fine-grained tracking of the choreography saga's internal progress.
 *
 * <p>Stored in {@code orders.saga_status} alongside the coarser {@code OrderStatus}.
 * Used primarily for debugging and observability — it answers "where exactly is
 * this saga right now?" rather than just "is it running or done?".</p>
 *
 * <p>Typical happy-path progression:</p>
 * <pre>
 *   STARTED → INVENTORY_STEP_COMPLETED → PAYMENT_STEP_COMPLETED → COMPLETED
 * </pre>
 */
public enum SagaStatus {

    /** Saga initiated — order persisted, order.created queued in outbox. */
    STARTED,

    /** Payment charge attempt in progress (not currently set — reserved for future use). */
    PAYMENT_STEP_STARTED,

    /** Payment succeeded — order advancing to CONFIRMED. */
    PAYMENT_STEP_COMPLETED,

    /** Payment failed — initiating compensation. */
    PAYMENT_STEP_FAILED,

    /** Inventory reservation in progress (not currently set — reserved for future use). */
    INVENTORY_STEP_STARTED,

    /** Inventory reserved — awaiting payment charge. */
    INVENTORY_STEP_COMPLETED,

    /** Inventory reservation failed — saga aborting. */
    INVENTORY_STEP_FAILED,

    /** Compensation in progress (e.g. customer cancelled a CONFIRMED order). */
    COMPENSATING,

    /** Compensation completed — all side-effects rolled back. */
    COMPENSATED,

    /** Saga finished successfully — order is CONFIRMED. */
    COMPLETED,

    /** Saga failed permanently — order is FAILED. */
    FAILED
}
