package com.paymentplatform.commonlib.exception;

import lombok.Getter;

/**
 * Thrown when a single-product reservation fails due to insufficient stock.
 *
 * <p>Currently not thrown by {@code InventoryService} — the service uses the
 * two-pass reservation pattern (check all → reserve all) and publishes
 * {@code inventory.failed} on failure instead of throwing.
 * Kept for use by direct REST reservation endpoints if added in future.</p>
 */
@Getter
public class InventoryReservationException extends RuntimeException {

    /** The product that could not be reserved. */
    private final String productId;

    /** Number of units requested by the order. */
    private final int requestedQuantity;

    /** Number of units actually in stock. */
    private final int availableQuantity;

    public InventoryReservationException(String productId, int requestedQuantity, int availableQuantity) {
        super("Cannot reserve " + requestedQuantity + " units of product " + productId + ": only " + availableQuantity + " available");
        this.productId = productId;
        this.requestedQuantity = requestedQuantity;
        this.availableQuantity = availableQuantity;
    }
}
