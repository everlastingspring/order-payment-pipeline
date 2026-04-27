package com.paymentplatform.commonlib.exception;

import lombok.Getter;

/**
 * Thrown when a requested entity does not exist in the database.
 *
 * <p>Caught by {@code GlobalExceptionHandler} in each service and translated
 * to a 404 Not Found {@link com.paymentplatform.commonlib.dto.ErrorResponse}.</p>
 */
@Getter
public class ResourceNotFoundException extends RuntimeException {

    /** Domain type of the missing resource (e.g. "Order", "Payment", "Wallet"). */
    private final String resourceType;

    /** The ID that was looked up (UUID or String). Included in the error message. */
    private final Object resourceId;

    public ResourceNotFoundException(String resourceType, Object resourceId) {
        super(resourceType + " not found with id: " + resourceId);
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }
}
