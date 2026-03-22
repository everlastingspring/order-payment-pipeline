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
public class PaymentFailedEvent extends BaseEvent {

    private String paymentId;
    private String orderId;
    private String customerId;
    private MoneyDto attemptedAmount;
    private PaymentMethod paymentMethod;
    private String failureReason;
    private String failureCode;
    private Instant failedAt;

    public PaymentFailedEvent(String paymentId, String orderId, String customerId,
                              MoneyDto attemptedAmount, PaymentMethod paymentMethod,
                              String failureReason, String failureCode) {
        super(KafkaTopics.PAYMENT_FAILED);
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.customerId = customerId;
        this.attemptedAmount = attemptedAmount;
        this.paymentMethod = paymentMethod;
        this.failureReason = failureReason;
        this.failureCode = failureCode;
        this.failedAt = Instant.now();
    }
}
