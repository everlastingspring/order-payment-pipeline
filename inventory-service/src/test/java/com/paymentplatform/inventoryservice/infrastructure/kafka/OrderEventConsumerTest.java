package com.paymentplatform.inventoryservice.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentplatform.commonlib.dto.MoneyDto;
import com.paymentplatform.commonlib.dto.OrderItemDto;
import com.paymentplatform.commonlib.enums.OrderStatus;
import com.paymentplatform.commonlib.enums.PaymentMethod;
import com.paymentplatform.commonlib.events.OrderCancelledEvent;
import com.paymentplatform.commonlib.events.OrderCreatedEvent;
import com.paymentplatform.commonlib.events.OrderFailedEvent;
import com.paymentplatform.inventoryservice.application.service.InventoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("Inventory OrderEventConsumer Tests")
class OrderEventConsumerTest {

    @Mock
    private InventoryService inventoryService;

    @Mock
    private Acknowledgment acknowledgment;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    @DisplayName("handleOrderCreated reserves inventory and acknowledges")
    void handleOrderCreated_reservesInventory() throws Exception {
        OrderEventConsumer consumer = new OrderEventConsumer(inventoryService, objectMapper);
        OrderCreatedEvent event = new OrderCreatedEvent(
                "order-1",
                "customer-1",
                "user@example.com",
                new MoneyDto(new BigDecimal("100.00"), "INR"),
                PaymentMethod.WALLET,
                List.of(new OrderItemDto(
                        "p-1",
                        "Item",
                        "sku-1",
                        2,
                        new MoneyDto(new BigDecimal("50.00"), "INR"),
                        new MoneyDto(new BigDecimal("100.00"), "INR")
                )),
                "addr"
        );

        consumer.handleOrderCreated(objectMapper.writeValueAsString(event), acknowledgment);

        ArgumentCaptor<OrderCreatedEvent> captor = ArgumentCaptor.forClass(OrderCreatedEvent.class);
        verify(inventoryService).reserveInventory(captor.capture());
        assertThat(captor.getValue().getOrderId()).isEqualTo("order-1");
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("handleOrderCancelled releases inventory and acknowledges")
    void handleOrderCancelled_releasesInventory() throws Exception {
        OrderEventConsumer consumer = new OrderEventConsumer(inventoryService, objectMapper);
        OrderCancelledEvent event = new OrderCancelledEvent(
                "order-1", "customer-1", "user@example.com",
                "user requested", "customer", OrderStatus.CONFIRMED, null
        );

        consumer.handleOrderCancelled(objectMapper.writeValueAsString(event), acknowledgment);

        ArgumentCaptor<OrderCancelledEvent> captor = ArgumentCaptor.forClass(OrderCancelledEvent.class);
        verify(inventoryService).releaseInventory(captor.capture());
        assertThat(captor.getValue().getOrderId()).isEqualTo("order-1");
        assertThat(captor.getValue().getCancellationReason()).isEqualTo("user requested");
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("handleOrderFailed releases inventory only for payment failures")
    void handleOrderFailed_releasesOnlyForPaymentFailures() throws Exception {
        OrderEventConsumer consumer = new OrderEventConsumer(inventoryService, objectMapper);
        OrderFailedEvent paymentFailed = new OrderFailedEvent("order-1", "customer-1", "user@example.com", "declined", "PAYMENT");
        OrderFailedEvent inventoryFailed = new OrderFailedEvent("order-2", "customer-1", "user@example.com", "oos", "INVENTORY");

        consumer.handleOrderFailed(objectMapper.writeValueAsString(paymentFailed), acknowledgment);
        consumer.handleOrderFailed(objectMapper.writeValueAsString(inventoryFailed), acknowledgment);

        verify(inventoryService).releaseInventoryByOrderId("order-1");
        verify(acknowledgment, times(2)).acknowledge();
    }

    @Test
    @DisplayName("invalid payload raises runtime exception and does not acknowledge")
    void handleOrderCreated_invalidPayload_throws() {
        OrderEventConsumer consumer = new OrderEventConsumer(inventoryService, objectMapper);

        assertThrows(RuntimeException.class, () -> consumer.handleOrderCreated("not-json", acknowledgment));

        verifyNoInteractions(inventoryService);
        verifyNoInteractions(acknowledgment);
    }
}
