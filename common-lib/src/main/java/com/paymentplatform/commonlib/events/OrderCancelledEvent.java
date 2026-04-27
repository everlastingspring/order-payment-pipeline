package com.paymentplatform.commonlib.events;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.paymentplatform.commonlib.constants.KafkaTopics;
import com.paymentplatform.commonlib.dto.MoneyDto;
import com.paymentplatform.commonlib.enums.OrderStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * Published to {@code order.cancelled} when an order is explicitly cancelled.
 *
 * <p>Consumers:</p>
 * <ul>
 *   <li><b>wallet-service</b>: issues a refund ledger entry if
 *       {@link #refundAmount} is non-zero (i.e. payment was already completed).</li>
 *   <li><b>inventory-service</b>: releases any reserved stock back to available.</li>
 *   <li><b>notification-service</b>: sends a cancellation confirmation email.</li>
 * </ul>
 *
 * <p>Cancellation rules (enforced by {@code OrderService.cancelOrder()}):</p>
 * <ul>
 *   <li>{@code PENDING / INVENTORY_RESERVED}: no refund — payment was never taken.</li>
 *   <li>{@code CONFIRMED}: full refund — payment was debited before cancellation.</li>
 *   <li>{@code CANCELLED / FAILED}: blocked — terminal states cannot be cancelled again.</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderCancelledEvent extends BaseEvent {

    /** The order being cancelled. */
    private String orderId;

    /** Owner of the order. */
    private String customerId;

    /** Customer email for cancellation notification. */
    private String customerEmail;

    /** Human-readable reason provided at cancellation time (e.g. "Changed my mind"). */
    private String cancellationReason;

    /** Who initiated the cancellation — "CUSTOMER" or "SYSTEM". */
    private String cancelledBy;

    /**
     * The order status BEFORE it was set to CANCELLED.
     * Captured before mutation — see CLAUDE.md gotcha section.
     * Used by downstream consumers to determine compensation (e.g. inventory release
     * only needed if status was INVENTORY_RESERVED or CONFIRMED).
     */
    private OrderStatus previousStatus;

    /**
     * Amount to refund to the customer's wallet.
     * Zero (not null) when no payment was taken ({@code previousStatus = PENDING / INVENTORY_RESERVED}).
     * Equals order total when {@code previousStatus = CONFIRMED}.
     */
    private MoneyDto refundAmount;

    /** Timestamp when the cancellation was persisted. */
    private Instant cancelledAt;

    public OrderCancelledEvent(String orderId, String customerId, String customerEmail,
                               String cancellationReason, String cancelledBy,
                               OrderStatus previousStatus, MoneyDto refundAmount) {
        super(KafkaTopics.ORDER_CANCELLED);
        this.orderId = orderId;
        this.customerId = customerId;
        this.customerEmail = customerEmail;
        this.cancellationReason = cancellationReason;
        this.cancelledBy = cancelledBy;
        this.previousStatus = previousStatus;
        this.refundAmount = refundAmount;
        this.cancelledAt = Instant.now();
    }
}
