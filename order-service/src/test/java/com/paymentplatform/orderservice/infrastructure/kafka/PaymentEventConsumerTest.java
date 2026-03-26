package com.paymentplatform.orderservice.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentplatform.commonlib.dto.MoneyDto;
import com.paymentplatform.commonlib.enums.PaymentMethod;
import com.paymentplatform.commonlib.events.PaymentCompletedEvent;
import com.paymentplatform.commonlib.events.PaymentFailedEvent;
import com.paymentplatform.orderservice.application.saga.OrderSagaManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentEventConsumer Tests")
class PaymentEventConsumerTest {

    @Mock
    private OrderSagaManager sagaManager;

    @Mock
    private Acknowledgment acknowledgment;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    @DisplayName("onPaymentCompleted forwards event to saga manager and acknowledges")
    void onPaymentCompleted_forwardsEvent() throws Exception {
        PaymentEventConsumer consumer = new PaymentEventConsumer(sagaManager, objectMapper);
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                "payment-1", "order-1", "customer-1",
                new MoneyDto(new BigDecimal("100.00"), "INR"), PaymentMethod.WALLET, "TXN-1"
        );

        consumer.onPaymentCompleted(objectMapper.writeValueAsString(event), acknowledgment);

        ArgumentCaptor<PaymentCompletedEvent> captor = ArgumentCaptor.forClass(PaymentCompletedEvent.class);
        verify(sagaManager).handlePaymentCompleted(captor.capture());
        assertThat(captor.getValue().getOrderId()).isEqualTo("order-1");
        assertThat(captor.getValue().getPaymentId()).isEqualTo("payment-1");
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("onPaymentFailed forwards event to saga manager and acknowledges")
    void onPaymentFailed_forwardsEvent() throws Exception {
        PaymentEventConsumer consumer = new PaymentEventConsumer(sagaManager, objectMapper);
        PaymentFailedEvent event = new PaymentFailedEvent(
                "payment-1", "order-1", "customer-1",
                new MoneyDto(new BigDecimal("100.00"), "INR"), PaymentMethod.WALLET, "declined", "DECLINED"
        );

        consumer.onPaymentFailed(objectMapper.writeValueAsString(event), acknowledgment);

        ArgumentCaptor<PaymentFailedEvent> captor = ArgumentCaptor.forClass(PaymentFailedEvent.class);
        verify(sagaManager).handlePaymentFailed(captor.capture());
        assertThat(captor.getValue().getOrderId()).isEqualTo("order-1");
        assertThat(captor.getValue().getFailureCode()).isEqualTo("DECLINED");
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("invalid payload throws runtime exception")
    void onPaymentFailed_invalidPayload_throws() {
        PaymentEventConsumer consumer = new PaymentEventConsumer(sagaManager, objectMapper);

        assertThatThrownBy(() -> consumer.onPaymentFailed("bad-json", acknowledgment))
                .isInstanceOf(RuntimeException.class);

        verifyNoInteractions(sagaManager, acknowledgment);
    }
}
