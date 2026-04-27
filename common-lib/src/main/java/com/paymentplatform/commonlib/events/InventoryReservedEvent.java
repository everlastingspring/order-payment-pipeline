package com.paymentplatform.commonlib.events;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.paymentplatform.commonlib.constants.KafkaTopics;
import com.paymentplatform.commonlib.dto.MoneyDto;
import com.paymentplatform.commonlib.enums.PaymentMethod;
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
 * Published to {@code inventory.reserved} after all order items are successfully reserved.
 *
 * <p>Consumers:</p>
 * <ul>
 *   <li><b>payment-service</b>: triggers payment charge — sequential saga design ensures
 *       a customer is only charged after stock is confirmed locked.</li>
 *   <li><b>order-service</b>: advances the order from {@code PENDING} to
 *       {@code INVENTORY_RESERVED}.</li>
 * </ul>
 *
 * <p>The payment-related fields ({@link #customerId}, {@link #customerEmail},
 * {@link #totalAmount}, {@link #paymentMethod}) are "passed through" from
 * {@code OrderCreatedEvent} so that payment-service has everything it needs
 * without needing to call order-service via REST.</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InventoryReservedEvent extends BaseEvent {

    /** Unique ID for the reservation batch (can span multiple products in one order). */
    private String reservationId;

    /** The order that triggered the reservation. */
    private String orderId;

    /** Per-product reservation details including warehouse location and locked quantity. */
    private List<ReservedItem> reservedItems;

    /** Timestamp when stock was locked in inventory_db. */
    private Instant reservedAt;

    /**
     * Reservation expiry wall-clock time.
     * inventory-service will release the stock and publish inventory.failed
     * if the order saga hasn't completed by this time.
     */
    private Instant expiresAt;

    // ── Payment passthrough ──
    // These fields are carried from OrderCreatedEvent so payment-service
    // can charge after consuming this event (sequential saga: inventory → payment).
    // Avoids an extra REST call from payment-service → order-service.

    /** Customer to be charged — forwarded from OrderCreatedEvent. */
    private String customerId;

    /** Customer email for payment receipt — forwarded from OrderCreatedEvent. */
    private String customerEmail;

    /** Amount to charge — forwarded from OrderCreatedEvent. */
    private MoneyDto totalAmount;

    /** Payment method the customer selected — forwarded from OrderCreatedEvent. */
    private PaymentMethod paymentMethod;

    public InventoryReservedEvent(String reservationId, String orderId,
                                  List<ReservedItem> reservedItems, Instant expiresAt) {
        super(KafkaTopics.INVENTORY_RESERVED);
        this.reservationId = reservationId;
        this.orderId = orderId;
        this.reservedItems = reservedItems;
        this.reservedAt = Instant.now();
        this.expiresAt = expiresAt;
    }

    /**
     * Details of a single product successfully reserved in a warehouse.
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ReservedItem {
        /** Product UUID matching {@code products.id} in inventory_db. */
        private String productId;

        /** Stock-keeping unit — human-readable product code (e.g. "MOUSE-WL-001"). */
        private String sku;

        /** Number of units locked for this order. */
        private int quantity;

        /** Warehouse from which stock was reserved (e.g. "WH-001"). */
        private String warehouseId;
    }
}
