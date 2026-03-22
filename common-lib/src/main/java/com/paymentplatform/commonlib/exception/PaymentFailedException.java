package com.paymentplatform.commonlib.exception;

import com.paymentplatform.commonlib.enums.PaymentStatus;
import lombok.Getter;

@Getter
public class PaymentFailedException extends RuntimeException {

    private final String paymentId;
    private final String reason;
    private final PaymentStatus currentStatus;

    public PaymentFailedException(String paymentId, String reason, PaymentStatus currentStatus) {
        super("Payment " + paymentId + " failed: " + reason);
        this.paymentId = paymentId;
        this.reason = reason;
        this.currentStatus = currentStatus;
    }
}
