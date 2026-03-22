package com.paymentplatform.commonlib.exception;

import com.paymentplatform.commonlib.enums.OrderStatus;
import lombok.Getter;

@Getter
public class InvalidOrderStateException extends RuntimeException {

    private final String orderId;
    private final OrderStatus currentStatus;
    private final OrderStatus attemptedTransition;

    public InvalidOrderStateException(String orderId, OrderStatus currentStatus, OrderStatus attemptedTransition) {
        super("Invalid state transition for order " + orderId + ": cannot move from " + currentStatus + " to " + attemptedTransition);
        this.orderId = orderId;
        this.currentStatus = currentStatus;
        this.attemptedTransition = attemptedTransition;
    }
}
