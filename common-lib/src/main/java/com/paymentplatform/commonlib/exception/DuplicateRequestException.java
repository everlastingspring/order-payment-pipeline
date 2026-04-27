package com.paymentplatform.commonlib.exception;

import lombok.Getter;

/**
 * Thrown when a payment request is detected as a duplicate via the idempotency_keys table.
 *
 * <p>Not currently thrown by active code (duplicate detection returns silently),
 * but reserved for explicit rejection when the caller needs a clear signal
 * that the request was already processed.</p>
 */
@Getter
public class DuplicateRequestException extends RuntimeException {

    /** The idempotency key that already exists in idempotency_keys. */
    private final String idempotencyKey;

    public DuplicateRequestException(String idempotencyKey) {
        super("Duplicate request detected for idempotency key: " + idempotencyKey);
        this.idempotencyKey = idempotencyKey;
    }
}
