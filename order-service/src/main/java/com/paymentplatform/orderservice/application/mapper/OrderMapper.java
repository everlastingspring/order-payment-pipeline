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

/**
 * MapStruct mapper converting {@link Order} and {@link OrderItem} JPA entities to
 * {@link com.paymentplatform.commonlib.dto.OrderDto} and {@link OrderItemDto} API objects.
 *
 * <p><strong>Custom mappings:</strong></p>
 * <ul>
 *   <li>{@code order.id} → {@code orderId} (DTO uses a different field name for clarity)</li>
 *   <li>{@code totalAmount} ({@code BigDecimal}) + {@code currency} → {@code MoneyDto}
 *       via the {@link #toMoney(BigDecimal, String)} helper to avoid a separate mapper</li>
 *   <li>{@code unitPrice} and {@code subtotal} on {@link OrderItem} → {@code MoneyDto} similarly</li>
 * </ul>
 */
@Mapper(componentModel = "spring")
public interface OrderMapper {

    /**
     * Maps an {@link Order} entity to its DTO representation.
     * Wraps {@code totalAmount} + {@code currency} into a {@link MoneyDto}.
     *
     * @param order the order entity
     * @return populated {@link com.paymentplatform.commonlib.dto.OrderDto}
     */
    @Mapping(target = "orderId", source = "id")
    @Mapping(target = "totalAmount", expression = "java(toMoney(order.getTotalAmount(), order.getCurrency()))")
    OrderDto toDto(Order order);

    /**
     * Convenience method to map a list of orders to DTOs.
     *
     * @param orders list of order entities
     * @return list of DTOs
     */
    List<OrderDto> toDtoList(List<Order> orders);

    /**
     * Maps an {@link OrderItem} entity to its DTO.
     * Wraps {@code unitPrice} and {@code subtotal} into {@link MoneyDto}s.
     *
     * @param item the order item entity
     * @return populated {@link OrderItemDto}
     */
    @Mapping(target = "unitPrice", expression = "java(toMoney(item.getUnitPrice(), item.getCurrency()))")
    @Mapping(target = "subtotal", expression = "java(toMoney(item.getSubtotal(), item.getCurrency()))")
    OrderItemDto toItemDto(OrderItem item);

    /**
     * Constructs a {@link MoneyDto} from a raw amount and currency code.
     * Used by MapStruct expression mappings above.
     *
     * @param amount   the monetary amount (BigDecimal for precision)
     * @param currency ISO 4217 currency code
     * @return populated {@link MoneyDto}
     */
    default MoneyDto toMoney(BigDecimal amount, String currency) {
        return MoneyDto.builder()
                .amount(amount)
                .currency(currency)
                .build();
    }
}
