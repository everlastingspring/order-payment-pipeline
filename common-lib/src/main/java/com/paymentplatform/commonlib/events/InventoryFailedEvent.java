package com.paymentplatform.commonlib.events;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.paymentplatform.commonlib.constants.KafkaTopics;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.List;

/**
 * Published to {@code inventory.failed} when one or more order items cannot be reserved.
 *
 * <p>Consumers:</p>
 * <ul>
 *   <li><b>order-service</b>: marks the order {@code FAILED} — no payment will be attempted.</li>
 *   <li><b>notification-service</b>: notifies the customer that their order could not be fulfilled.</li>
 * </ul>
 *
 * <p>Also published by {@code ReservationExpiryProcessor} when a reservation
 * exceeds its TTL without the saga completing.</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InventoryFailedEvent extends BaseEvent {

    /** The order that could not be fulfilled. */
    private String orderId;

    /** Per-product breakdown of what was requested vs what was available. */
    private List<FailedItem> failedItems;

    /** Human-readable summary of all unavailable products — logged and shown to the customer. */
    private String failureReason;

    /** Timestamp when the failure was detected. */
    private Instant failedAt;

    public InventoryFailedEvent(String orderId, List<FailedItem> failedItems, String failureReason) {
        super(KafkaTopics.INVENTORY_FAILED);
        this.orderId = orderId;
        this.failedItems = failedItems;
        this.failureReason = failureReason;
        this.failedAt = Instant.now();
    }

    /**
     * Per-product failure detail.
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FailedItem {
        /** Product UUID that could not be reserved. */
        private String productId;

        /** How many units were requested in the order. */
        private int requestedQuantity;

        /** How many units were actually in stock at reservation time. */
        private int availableQuantity;
    }
}
