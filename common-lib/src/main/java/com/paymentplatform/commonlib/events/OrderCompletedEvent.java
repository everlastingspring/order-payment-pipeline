package com.paymentplatform.commonlib.events;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.paymentplatform.commonlib.constants.KafkaTopics;
import com.paymentplatform.commonlib.dto.MoneyDto;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * Published to {@code order.completed} after the order reaches the {@code CONFIRMED} state.
 *
 * <p>Consumer: <b>notification-service</b> — sends the customer a final
 * "Your order is confirmed" email.</p>
 *
 * <p>This is the happy-path terminal event of the saga.
 * At this point: stock is reserved, payment is collected, order is confirmed.</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderCompletedEvent extends BaseEvent {

    /** The confirmed order. */
    private String orderId;

    /** Owner of the order. */
    private String customerId;

    /** Customer email for the final confirmation notification. */
    private String customerEmail;

    /** Total amount paid — shown in the confirmation email. */
    private MoneyDto totalAmount;

    /** Timestamp when the order was marked CONFIRMED. */
    private Instant completedAt;

    public OrderCompletedEvent(String orderId, String customerId, String customerEmail, MoneyDto totalAmount) {
        super(KafkaTopics.ORDER_COMPLETED);
        this.orderId = orderId;
        this.customerId = customerId;
        this.customerEmail = customerEmail;
        this.totalAmount = totalAmount;
        this.completedAt = Instant.now();
    }
}
