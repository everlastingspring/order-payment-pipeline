package com.paymentplatform.commonlib.events;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.paymentplatform.commonlib.constants.KafkaTopics;
import com.paymentplatform.commonlib.dto.MoneyDto;
import com.paymentplatform.commonlib.enums.PaymentMethod;
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
public class PaymentCompletedEvent extends BaseEvent {

    private String paymentId;
    private String orderId;
    private String customerId;
    private MoneyDto amount;
    private PaymentMethod paymentMethod;
    private String transactionReference;
    private Instant completedAt;

    public PaymentCompletedEvent(String paymentId, String orderId, String customerId,
                                 MoneyDto amount, PaymentMethod paymentMethod,
                                 String transactionReference) {
        super(KafkaTopics.PAYMENT_COMPLETED);
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.customerId = customerId;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.transactionReference = transactionReference;
        this.completedAt = Instant.now();
    }
}
