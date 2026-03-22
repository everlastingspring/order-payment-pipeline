package com.paymentplatform.commonlib.events;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.paymentplatform.commonlib.constants.KafkaTopics;
import com.paymentplatform.commonlib.dto.MoneyDto;
import com.paymentplatform.commonlib.dto.OrderItemDto;
import com.paymentplatform.commonlib.enums.PaymentMethod;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderCreatedEvent extends BaseEvent {

    private String orderId;
    private String customerId;
    private String customerEmail;
    private MoneyDto totalAmount;
    private PaymentMethod paymentMethod;
    private List<OrderItemDto> items;
    private String shippingAddress;
    private Instant createdAt;

    public OrderCreatedEvent(String orderId, String customerId, String customerEmail,
                             MoneyDto totalAmount, PaymentMethod paymentMethod,
                             List<OrderItemDto> items, String shippingAddress) {
        super(KafkaTopics.ORDER_CREATED);
        this.orderId = orderId;
        this.customerId = customerId;
        this.customerEmail = customerEmail;
        this.totalAmount = totalAmount;
        this.paymentMethod = paymentMethod;
        this.items = items;
        this.shippingAddress = shippingAddress;
        this.createdAt = Instant.now();
    }
}
