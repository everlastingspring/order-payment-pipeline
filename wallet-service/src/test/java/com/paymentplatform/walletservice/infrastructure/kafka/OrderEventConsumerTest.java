package com.paymentplatform.walletservice.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentplatform.commonlib.dto.MoneyDto;
import com.paymentplatform.commonlib.enums.OrderStatus;
import com.paymentplatform.commonlib.events.OrderCancelledEvent;
import com.paymentplatform.walletservice.application.service.WalletService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("Wallet OrderEventConsumer Tests")
class OrderEventConsumerTest {

    @Mock
    private WalletService walletService;

    @Mock
    private Acknowledgment acknowledgment;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    @DisplayName("handleOrderCancelled refunds wallet and acknowledges")
    void handleOrderCancelled_refundsWallet() throws Exception {
        OrderEventConsumer consumer = new OrderEventConsumer(walletService, objectMapper);
        OrderCancelledEvent event = new OrderCancelledEvent(
                "order-1", "customer-1", "user@example.com",
                "customer request", "customer", OrderStatus.CONFIRMED, new MoneyDto(new BigDecimal("50.00"), "INR")
        );

        consumer.handleOrderCancelled(objectMapper.writeValueAsString(event), acknowledgment);

        verify(walletService).refundForCancellation("customer-1", event.getRefundAmount(), "order-1");
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("invalid payload throws runtime exception")
    void handleOrderCancelled_invalidPayload_throws() {
        OrderEventConsumer consumer = new OrderEventConsumer(walletService, objectMapper);

        assertThatThrownBy(() -> consumer.handleOrderCancelled("bad-json", acknowledgment))
                .isInstanceOf(RuntimeException.class);

        verifyNoInteractions(walletService, acknowledgment);
    }
}
