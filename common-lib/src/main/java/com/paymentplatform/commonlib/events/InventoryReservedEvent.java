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

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InventoryReservedEvent extends BaseEvent {

    private String reservationId;
    private String orderId;
    private List<ReservedItem> reservedItems;
    private Instant reservedAt;
    private Instant expiresAt;

    // Payment passthrough — carried from OrderCreatedEvent so payment-service
    // can charge after consuming this event (sequential saga: inventory → payment)
    private String customerId;
    private String customerEmail;
    private MoneyDto totalAmount;
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

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ReservedItem {
        private String productId;
        private String sku;
        private int quantity;
        private String warehouseId;
    }
}
