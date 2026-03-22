package com.paymentplatform.commonlib.exception;

import lombok.Getter;

@Getter
public class DuplicateRequestException extends RuntimeException {

    private final String idempotencyKey;

    public DuplicateRequestException(String idempotencyKey) {
        super("Duplicate request detected for idempotency key: " + idempotencyKey);
        this.idempotencyKey = idempotencyKey;
    }
}
