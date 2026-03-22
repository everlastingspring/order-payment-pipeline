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

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderCancelledEvent extends BaseEvent {

    private String orderId;
    private String customerId;
    private String customerEmail;
    private String cancellationReason;
    private String cancelledBy;
    private OrderStatus previousStatus;
    private MoneyDto refundAmount;
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
