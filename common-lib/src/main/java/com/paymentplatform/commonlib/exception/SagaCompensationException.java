package com.paymentplatform.commonlib.exception;

import lombok.Getter;

@Getter
public class SagaCompensationException extends RuntimeException {

    private final String sagaId;
    private final String failedStep;
    private final String reason;

    public SagaCompensationException(String sagaId, String failedStep, String reason) {
        super("Saga " + sagaId + " compensation failed at step '" + failedStep + "': " + reason);
        this.sagaId = sagaId;
        this.failedStep = failedStep;
        this.reason = reason;
    }
}
