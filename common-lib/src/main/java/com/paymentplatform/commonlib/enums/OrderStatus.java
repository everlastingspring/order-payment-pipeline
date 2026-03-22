package com.paymentplatform.commonlib.enums;

public enum OrderStatus {
    PENDING,
    PAYMENT_PENDING,
    PAYMENT_FAILED,
    INVENTORY_PENDING,
    INVENTORY_FAILED,
    CONFIRMED,
    COMPLETED,
    CANCELLED
}
