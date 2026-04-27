package com.paymentplatform.commonlib.exception;

import com.paymentplatform.commonlib.enums.PaymentStatus;
import lombok.Getter;

/**
 * Thrown when a payment processing error is unrecoverable and must propagate up.
 *
 * <p>Distinct from a declined payment (which returns {@code GatewayResponse.failure}
 * and is handled gracefully by the saga). This exception is for unexpected
 * infrastructure errors that should surface to the caller.</p>
 */
@Getter
public class PaymentFailedException extends RuntimeException {

    /** The payment record that failed. */
    private final String paymentId;

    /** Detailed failure description. */
    private final String reason;

    /** The payment's status at the time of failure. */
    private final PaymentStatus currentStatus;

    public PaymentFailedException(String paymentId, String reason, PaymentStatus currentStatus) {
        super("Payment " + paymentId + " failed: " + reason);
        this.paymentId = paymentId;
        this.reason = reason;
        this.currentStatus = currentStatus;
    }
}
