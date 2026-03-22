package com.paymentplatform.commonlib.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.paymentplatform.commonlib.enums.PaymentMethod;
import com.paymentplatform.commonlib.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentDto {

    private String paymentId;
    private String orderId;
    private String customerId;
    private PaymentStatus status;
    private MoneyDto amount;
    private PaymentMethod paymentMethod;
    private String idempotencyKey;
    private String transactionReference;
    private String failureReason;
    private Instant createdAt;
    private Instant updatedAt;
}
