package com.paymentplatform.orderservice.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentplatform.commonlib.events.InventoryFailedEvent;
import com.paymentplatform.commonlib.events.InventoryReservedEvent;
import com.paymentplatform.orderservice.application.saga.OrderSagaManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryEventConsumer Tests")
class InventoryEventConsumerTest {

    @Mock
    private OrderSagaManager sagaManager;

    @Mock
    private Acknowledgment acknowledgment;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    @DisplayName("onInventoryReserved forwards event to saga manager and acknowledges")
    void onInventoryReserved_forwardsEvent() throws Exception {
        InventoryEventConsumer consumer = new InventoryEventConsumer(sagaManager, objectMapper);
        InventoryReservedEvent event = InventoryReservedEvent.builder()
                .orderId("order-1")
                .reservationId("reservation-1")
                .reservedItems(List.of())
                .expiresAt(Instant.now().plusSeconds(900))
                .build();

        consumer.onInventoryReserved(objectMapper.writeValueAsString(event), acknowledgment);

        ArgumentCaptor<InventoryReservedEvent> captor = ArgumentCaptor.forClass(InventoryReservedEvent.class);
        verify(sagaManager).handleInventoryReserved(captor.capture());
        assertThat(captor.getValue().getOrderId()).isEqualTo("order-1");
        assertThat(captor.getValue().getReservationId()).isEqualTo("reservation-1");
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("onInventoryFailed forwards event to saga manager and acknowledges")
    void onInventoryFailed_forwardsEvent() throws Exception {
        InventoryEventConsumer consumer = new InventoryEventConsumer(sagaManager, objectMapper);
        InventoryFailedEvent event = new InventoryFailedEvent("order-1", List.of(), "out of stock");

        consumer.onInventoryFailed(objectMapper.writeValueAsString(event), acknowledgment);

        ArgumentCaptor<InventoryFailedEvent> captor = ArgumentCaptor.forClass(InventoryFailedEvent.class);
        verify(sagaManager).handleInventoryFailed(captor.capture());
        assertThat(captor.getValue().getOrderId()).isEqualTo("order-1");
        assertThat(captor.getValue().getFailureReason()).isEqualTo("out of stock");
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("invalid payload throws runtime exception")
    void onInventoryReserved_invalidPayload_throws() {
        InventoryEventConsumer consumer = new InventoryEventConsumer(sagaManager, objectMapper);

        assertThatThrownBy(() -> consumer.onInventoryReserved("bad-json", acknowledgment))
                .isInstanceOf(RuntimeException.class);

        verifyNoInteractions(sagaManager, acknowledgment);
    }
}
