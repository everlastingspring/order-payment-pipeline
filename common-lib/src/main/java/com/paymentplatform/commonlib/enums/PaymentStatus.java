package com.paymentplatform.commonlib.enums;

/**
 * Lifecycle states of a payment record in payment_db.
 *
 * <p>Current active flow: {@code INITIATED → PROCESSING → COMPLETED / FAILED}</p>
 *
 * <p>{@code REFUNDED} and {@code PARTIALLY_REFUNDED} are reserved for future
 * direct refund endpoints (currently refunds are handled by wallet-service
 * as ledger credits, not by updating the payment record).</p>
 */
public enum PaymentStatus {

    /** Payment record created, not yet processed. */
    INITIATED,

    /** Charge attempt in progress — idempotency key is held. */
    PROCESSING,

    /** Charge succeeded — wallet debited or gateway confirmed. */
    COMPLETED,

    /** Charge attempt failed — no money moved. */
    FAILED,

    /** Full refund issued (future — not currently set by live code). */
    REFUNDED,

    /** Partial refund issued (future — not currently set by live code). */
    PARTIALLY_REFUNDED
}
