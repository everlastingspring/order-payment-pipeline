package com.paymentplatform.commonlib.exception;

import lombok.Getter;

@Getter
public class InventoryReservationException extends RuntimeException {

    private final String productId;
    private final int requestedQuantity;
    private final int availableQuantity;

    public InventoryReservationException(String productId, int requestedQuantity, int availableQuantity) {
        super("Cannot reserve " + requestedQuantity + " units of product " + productId + ": only " + availableQuantity + " available");
        this.productId = productId;
        this.requestedQuantity = requestedQuantity;
        this.availableQuantity = availableQuantity;
    }
}
