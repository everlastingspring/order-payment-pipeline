package com.paymentplatform.commonlib.exception;

import lombok.Getter;

/**
 * Thrown when a saga compensation step itself fails.
 *
 * <p>Compensation failures are especially dangerous because the primary step
 * already occurred (e.g. inventory was reserved) but the rollback failed
 * (e.g. cannot release stock). Manual intervention may be required.</p>
 */
@Getter
public class SagaCompensationException extends RuntimeException {

    /** The saga/order ID being compensated. */
    private final String sagaId;

    /** Which compensation step failed (e.g. "INVENTORY_RELEASE"). */
    private final String failedStep;

    /** Detailed reason why compensation failed. */
    private final String reason;

    public SagaCompensationException(String sagaId, String failedStep, String reason) {
        super("Saga " + sagaId + " compensation failed at step '" + failedStep + "': " + reason);
        this.sagaId = sagaId;
        this.failedStep = failedStep;
        this.reason = reason;
    }
}
