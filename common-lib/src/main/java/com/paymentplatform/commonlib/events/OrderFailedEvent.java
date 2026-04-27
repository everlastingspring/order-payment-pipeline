package com.paymentplatform.commonlib.events;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.paymentplatform.commonlib.constants.KafkaTopics;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * Published when the saga fails due to:
 *  - Inventory out of stock / unavailable  (inventory.failed path)
 *  - Payment declined / gateway error      (payment.failed path)
 *
 * inventory-service consumes this to release any reserved stock.
 * notification-service consumes this to notify the customer.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderFailedEvent extends BaseEvent {

    /** The order that failed. */
    private String orderId;

    /** Customer who placed the order. */
    private String customerId;

    /** Customer email for failure notification. */
    private String customerEmail;

    /** Human-readable reason (e.g. "Inventory unavailable: out of stock"). */
    private String failureReason;

    /**
     * Which saga step failed.
     * <ul>
     *   <li>{@code "INVENTORY"}: stock was never reserved — no stock to release.</li>
     *   <li>{@code "PAYMENT"}: stock WAS reserved — inventory-service must release it.</li>
     * </ul>
     * inventory-service checks this before deciding whether to run compensation.
     */
    private String failedStep;

    /** Timestamp when the failure was recorded. */
    private Instant failedAt;

    public OrderFailedEvent(String orderId, String customerId, String customerEmail,
                            String failureReason, String failedStep) {
        super(KafkaTopics.ORDER_FAILED);
        this.orderId = orderId;
        this.customerId = customerId;
        this.customerEmail = customerEmail;
        this.failureReason = failureReason;
        this.failedStep = failedStep;
        this.failedAt = Instant.now();
    }
}
