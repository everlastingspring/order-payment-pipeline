package com.paymentplatform.commonlib.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.paymentplatform.commonlib.enums.OrderStatus;
import com.paymentplatform.commonlib.enums.PaymentMethod;
import com.paymentplatform.commonlib.enums.SagaStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * API transfer object representing a customer order.
 *
 * <p>Returned by {@code GET /api/orders/{orderId}} and {@code POST /api/orders}.
 * Includes the current saga status so callers can distinguish between "order created" and
 * "saga in-progress" without needing to know the internal state machine transitions.</p>
 *
 * <p>{@code @JsonInclude(NON_NULL)} ensures that null optional fields ({@code paymentId},
 * {@code customerEmail}) are omitted from the JSON response rather than serialised as
 * {@code null}.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderDto {

    /** UUID of the order (mapped from {@code Order.id}). */
    private String orderId;

    /** Customer who placed the order. */
    private String customerId;

    /** Customer's email — used by notification-service; may be null in older records. */
    private String customerEmail;

    /** Current order lifecycle state (PENDING → INVENTORY_RESERVED → CONFIRMED or FAILED). */
    private OrderStatus status;

    /** Current saga coordination status — complements {@link #status} for saga monitoring. */
    private SagaStatus sagaStatus;

    /** Line items in the order. */
    private List<OrderItemDto> items;

    /** Total monetary value of all items combined. */
    private MoneyDto totalAmount;

    /** Payment method selected at order creation. Currently only WALLET is active. */
    private PaymentMethod paymentMethod;

    /** Delivery address — freeform string. */
    private String shippingAddress;

    /** UUID of the associated payment record; null until the payment step completes. */
    private String paymentId;

    /** When the order was created. */
    private Instant createdAt;

    /** When the order was last updated (status change, payment linked, etc.). */
    private Instant updatedAt;
}
