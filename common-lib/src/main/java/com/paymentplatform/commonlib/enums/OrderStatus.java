package com.paymentplatform.commonlib.enums;

public enum OrderStatus {
    PENDING,
    INVENTORY_RESERVED,   // inventory locked, awaiting payment
    CONFIRMED,            // payment succeeded → saga complete
    COMPLETED,            // final terminal state (alias kept for compatibility)
    CANCELLED,            // explicit user/admin cancellation
    FAILED                // saga failure: inventory out-of-stock OR payment failed
}
