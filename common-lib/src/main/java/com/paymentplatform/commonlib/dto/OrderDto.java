package com.paymentplatform.commonlib.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.paymentplatform.commonlib.enums.OrderStatus;
import com.paymentplatform.commonlib.enums.PaymentMethod;
import com.paymentplatform.commonlib.enums.SagaStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderDto {

    private String orderId;
    private String customerId;
    private String customerEmail;
    private OrderStatus status;
    private SagaStatus sagaStatus;
    private List<OrderItemDto> items;
    private MoneyDto totalAmount;
    private PaymentMethod paymentMethod;
    private String shippingAddress;
    private String paymentId;
    private Instant createdAt;
    private Instant updatedAt;
}
