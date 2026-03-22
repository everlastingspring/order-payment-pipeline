package com.paymentplatform.orderservice.application.mapper;

import com.paymentplatform.commonlib.dto.MoneyDto;
import com.paymentplatform.commonlib.dto.OrderDto;
import com.paymentplatform.commonlib.dto.OrderItemDto;
import com.paymentplatform.orderservice.domain.entity.Order;
import com.paymentplatform.orderservice.domain.entity.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;
import java.util.List;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    @Mapping(target = "orderId", source = "id")
    @Mapping(target = "totalAmount", expression = "java(toMoney(order.getTotalAmount(), order.getCurrency()))")
    OrderDto toDto(Order order);

    List<OrderDto> toDtoList(List<Order> orders);

    @Mapping(target = "unitPrice", expression = "java(toMoney(item.getUnitPrice(), item.getCurrency()))")
    @Mapping(target = "subtotal", expression = "java(toMoney(item.getSubtotal(), item.getCurrency()))")
    OrderItemDto toItemDto(OrderItem item);

    default MoneyDto toMoney(BigDecimal amount, String currency) {
        return MoneyDto.builder()
                .amount(amount)
                .currency(currency)
                .build();
    }
}
