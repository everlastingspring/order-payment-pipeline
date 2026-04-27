package com.paymentplatform.commonlib.exception;

import com.paymentplatform.commonlib.enums.OrderStatus;
import lombok.Getter;

/**
 * Thrown when an operation is attempted on an order that is in an incompatible state.
 *
 * <p>Primary use case: {@code OrderService.cancelOrder()} throws this
 * when trying to cancel an order that is already {@code CANCELLED} or {@code FAILED}.</p>
 *
 * <p>Caught by {@code GlobalExceptionHandler} and translated to a 409 Conflict response.</p>
 */
@Getter
public class InvalidOrderStateException extends RuntimeException {

    /** The order on which the invalid transition was attempted. */
    private final String orderId;

    /** The order's actual current status. */
    private final OrderStatus currentStatus;

    /** The status transition that was requested but is not allowed from {@link #currentStatus}. */
    private final OrderStatus attemptedTransition;

    public InvalidOrderStateException(String orderId, OrderStatus currentStatus, OrderStatus attemptedTransition) {
        super("Invalid state transition for order " + orderId + ": cannot move from " + currentStatus + " to " + attemptedTransition);
        this.orderId = orderId;
        this.currentStatus = currentStatus;
        this.attemptedTransition = attemptedTransition;
    }
}
