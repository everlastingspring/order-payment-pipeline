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

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InventoryFailedEvent extends BaseEvent {

    private String orderId;
    private List<FailedItem> failedItems;
    private String failureReason;
    private Instant failedAt;

    public InventoryFailedEvent(String orderId, List<FailedItem> failedItems, String failureReason) {
        super(KafkaTopics.INVENTORY_FAILED);
        this.orderId = orderId;
        this.failedItems = failedItems;
        this.failureReason = failureReason;
        this.failedAt = Instant.now();
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FailedItem {
        private String productId;
        private int requestedQuantity;
        private int availableQuantity;
    }
}
