package com.paymentplatform.paymentservice.application.mapper;

import com.paymentplatform.commonlib.dto.MoneyDto;
import com.paymentplatform.commonlib.dto.PaymentDto;
import com.paymentplatform.paymentservice.domain.entity.Payment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.math.BigDecimal;

/**
 * MapStruct mapper converting {@link Payment} JPA entities to
 * {@link com.paymentplatform.commonlib.dto.PaymentDto} API objects.
 *
 * <p><strong>Custom mappings:</strong></p>
 * <ul>
 *   <li>{@code payment.id} → {@code paymentId}</li>
 *   <li>{@code payment.amount} + {@code payment.currency} → {@link MoneyDto} via
 *       the {@link #toMoneyDto(Payment)} named helper (MapStruct cannot auto-compose
 *       two separate source fields into a nested DTO).</li>
 * </ul>
 */
@Mapper(componentModel = "spring")
public interface PaymentMapper {

    /**
     * Maps a {@link Payment} entity to its DTO representation.
     *
     * @param payment the payment entity
     * @return populated {@link com.paymentplatform.commonlib.dto.PaymentDto}
     */
    @Mapping(target = "paymentId", source = "id")
    @Mapping(target = "amount", source = ".", qualifiedByName = "toMoneyDto")
    PaymentDto toDto(Payment payment);

    /**
     * Constructs a {@link MoneyDto} from the payment's amount and currency fields.
     * Called by MapStruct via the {@code @Named("toMoneyDto")} qualifier.
     *
     * @param payment the payment entity carrying {@code amount} and {@code currency}
     * @return populated {@link MoneyDto}
     */
    @Named("toMoneyDto")
    default MoneyDto toMoneyDto(Payment payment) {
        return MoneyDto.builder()
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .build();
    }
}
