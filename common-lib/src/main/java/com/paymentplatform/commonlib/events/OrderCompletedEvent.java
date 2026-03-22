package com.paymentplatform.commonlib.events;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.paymentplatform.commonlib.constants.KafkaTopics;
import com.paymentplatform.commonlib.dto.MoneyDto;
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
public class OrderCompletedEvent extends BaseEvent {

    private String orderId;
    private String customerId;
    private String customerEmail;
    private MoneyDto totalAmount;
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
