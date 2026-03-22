package com.paymentplatform.paymentservice.application.mapper;

import com.paymentplatform.commonlib.dto.MoneyDto;
import com.paymentplatform.commonlib.dto.PaymentDto;
import com.paymentplatform.paymentservice.domain.entity.Payment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.math.BigDecimal;

@Mapper(componentModel = "spring")
public interface PaymentMapper {

    @Mapping(target = "paymentId", source = "id")
    @Mapping(target = "amount", source = ".", qualifiedByName = "toMoneyDto")
    PaymentDto toDto(Payment payment);

    @Named("toMoneyDto")
    default MoneyDto toMoneyDto(Payment payment) {
        return MoneyDto.builder()
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .build();
    }
}
