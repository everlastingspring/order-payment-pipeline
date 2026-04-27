package com.paymentplatform.commonlib.events;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.paymentplatform.commonlib.constants.KafkaTopics;
import com.paymentplatform.commonlib.dto.MoneyDto;
import com.paymentplatform.commonlib.dto.OrderItemDto;
import com.paymentplatform.commonlib.enums.PaymentMethod;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.List;

/**
 * Published to {@code order.created} when a new order is persisted.
 *
 * <p>Consumers:</p>
 * <ul>
 *   <li><b>inventory-service</b>: reserves stock for each {@link #items} entry.</li>
 *   <li><b>notification-service</b>: sends an order-confirmation email.</li>
 * </ul>
 *
 * <p>Payment-service does NOT consume this topic — it waits for
 * {@code inventory.reserved} so that payment is only attempted after
 * stock is confirmed available (sequential saga).</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderCreatedEvent extends BaseEvent {

    /** UUID of the newly created order (matches {@code orders.id} in order_db). */
    private String orderId;

    /** Business-level customer identifier (not a DB UUID — could be an external system ID). */
    private String customerId;

    /** Customer's email address — used by notification-service for confirmations. */
    private String customerEmail;

    /** Sum of all line-item subtotals including currency. */
    private MoneyDto totalAmount;

    /** The payment method selected by the customer at checkout. */
    private PaymentMethod paymentMethod;

    /** Line items included in the order. inventory-service iterates these to reserve stock. */
    private List<OrderItemDto> items;

    /** Delivery address — informational, not used by any downstream service logic. */
    private String shippingAddress;

    /** Timestamp when the order row was persisted in order_db. */
    private Instant createdAt;

    public OrderCreatedEvent(String orderId, String customerId, String customerEmail,
                             MoneyDto totalAmount, PaymentMethod paymentMethod,
                             List<OrderItemDto> items, String shippingAddress) {
        super(KafkaTopics.ORDER_CREATED);
        this.orderId = orderId;
        this.customerId = customerId;
        this.customerEmail = customerEmail;
        this.totalAmount = totalAmount;
        this.paymentMethod = paymentMethod;
        this.items = items;
        this.shippingAddress = shippingAddress;
        this.createdAt = Instant.now();
    }
}
