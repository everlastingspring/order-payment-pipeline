package com.paymentplatform.orderservice.application.saga;

import com.paymentplatform.commonlib.enums.OrderStatus;
import com.paymentplatform.commonlib.enums.SagaStatus;
import com.paymentplatform.commonlib.events.InventoryFailedEvent;
import com.paymentplatform.commonlib.events.InventoryReservedEvent;
import com.paymentplatform.commonlib.events.PaymentCompletedEvent;
import com.paymentplatform.commonlib.events.PaymentFailedEvent;
import com.paymentplatform.commonlib.exception.ResourceNotFoundException;
import com.paymentplatform.orderservice.application.service.OrderService;
import com.paymentplatform.orderservice.domain.entity.Order;
import com.paymentplatform.orderservice.domain.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderSagaManager")
class OrderSagaManagerTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderService orderService;

    private OrderSagaManager sagaManager;

    @BeforeEach
    void setUp() {
        sagaManager = new OrderSagaManager(orderRepository, orderService);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Order buildOrder(UUID id, OrderStatus status) {
        Order order = Order.builder()
                .customerId("customer-001")
                .customerEmail("test@example.com")
                .status(status)
                .sagaStatus(SagaStatus.STARTED)
                .totalAmount(new BigDecimal("4097.00"))
                .currency("INR")
                .paymentCompleted(false)
                .inventoryReserved(false)
                .items(new ArrayList<>())
                .build();
        try {
            var f = order.getClass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(order, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return order;
    }

    private InventoryReservedEvent inventoryReservedEvent(String orderId) {
        return InventoryReservedEvent.builder()
                .orderId(orderId)
                .customerId("customer-001")
                .reservationId(UUID.randomUUID().toString())
                .build();
    }

    private PaymentCompletedEvent paymentCompletedEvent(String orderId) {
        return PaymentCompletedEvent.builder()
                .orderId(orderId)
                .paymentId(UUID.randomUUID().toString())
                .customerId("customer-001")
                .build();
    }

    private InventoryFailedEvent inventoryFailedEvent(String orderId) {
        return InventoryFailedEvent.builder()
                .orderId(orderId)
                .failureReason("OUT_OF_STOCK")
                .build();
    }

    private PaymentFailedEvent paymentFailedEvent(String orderId) {
        return PaymentFailedEvent.builder()
                .orderId(orderId)
                .failureReason("INSUFFICIENT_WALLET_BALANCE")
                .build();
    }

    // ── handleInventoryReserved ───────────────────────────────────────────────

    @Nested
    @DisplayName("handleInventoryReserved")
    class HandleInventoryReserved {

        @Test
        @DisplayName("updates PENDING order to INVENTORY_RESERVED and sets inventoryReserved=true")
        void pendingOrder_updatesToInventoryReserved() {
            UUID id = UUID.randomUUID();
            Order order = buildOrder(id, OrderStatus.PENDING);
            when(orderRepository.findById(id)).thenReturn(Optional.of(order));
            when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            sagaManager.handleInventoryReserved(inventoryReservedEvent(id.toString()));

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(captor.capture());
            Order saved = captor.getValue();

            assertThat(saved.getStatus()).isEqualTo(OrderStatus.INVENTORY_RESERVED);
            assertThat(saved.getSagaStatus()).isEqualTo(SagaStatus.INVENTORY_STEP_COMPLETED);
            assertThat(saved.isInventoryReserved()).isTrue();
        }

        @ParameterizedTest(name = "skips save when order already in terminal state: {0}")
        @EnumSource(value = OrderStatus.class, names = {"CONFIRMED", "COMPLETED", "CANCELLED", "FAILED"})
        @DisplayName("skips save when order already in a final state")
        void finalState_skipsUpdate(OrderStatus status) {
            UUID id = UUID.randomUUID();
            Order order = buildOrder(id, status);
            when(orderRepository.findById(id)).thenReturn(Optional.of(order));

            sagaManager.handleInventoryReserved(inventoryReservedEvent(id.toString()));

            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when order does not exist")
        void orderNotFound_throwsResourceNotFoundException() {
            when(orderRepository.findById(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    sagaManager.handleInventoryReserved(inventoryReservedEvent(UUID.randomUUID().toString())))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── handlePaymentCompleted ────────────────────────────────────────────────

    @Nested
    @DisplayName("handlePaymentCompleted")
    class HandlePaymentCompleted {

        @Test
        @DisplayName("updates INVENTORY_RESERVED order to CONFIRMED, sets paymentCompleted=true, publishes order.completed")
        void inventoryReservedOrder_updatesToConfirmed_andPublishesCompleted() {
            UUID id = UUID.randomUUID();
            Order order = buildOrder(id, OrderStatus.INVENTORY_RESERVED);
            String paymentId = UUID.randomUUID().toString();
            when(orderRepository.findById(id)).thenReturn(Optional.of(order));
            when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            sagaManager.handlePaymentCompleted(PaymentCompletedEvent.builder()
                    .orderId(id.toString())
                    .paymentId(paymentId)
                    .customerId("customer-001")
                    .build());

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(captor.capture());
            Order saved = captor.getValue();

            assertThat(saved.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
            assertThat(saved.getSagaStatus()).isEqualTo(SagaStatus.COMPLETED);
            assertThat(saved.isPaymentCompleted()).isTrue();
            assertThat(saved.getPaymentId()).isEqualTo(paymentId);

            verify(orderService).publishOrderCompleted(saved);
        }

        @ParameterizedTest(name = "skips save when order already in terminal state: {0}")
        @EnumSource(value = OrderStatus.class, names = {"CONFIRMED", "COMPLETED", "CANCELLED", "FAILED"})
        @DisplayName("skips save and publishOrderCompleted when order already in final state")
        void finalState_skipsUpdate(OrderStatus status) {
            UUID id = UUID.randomUUID();
            Order order = buildOrder(id, status);
            when(orderRepository.findById(id)).thenReturn(Optional.of(order));

            sagaManager.handlePaymentCompleted(paymentCompletedEvent(id.toString()));

            verify(orderRepository, never()).save(any());
            verify(orderService, never()).publishOrderCompleted(any());
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when order does not exist")
        void orderNotFound_throwsResourceNotFoundException() {
            when(orderRepository.findById(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    sagaManager.handlePaymentCompleted(paymentCompletedEvent(UUID.randomUUID().toString())))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── handleInventoryFailed ─────────────────────────────────────────────────

    @Nested
    @DisplayName("handleInventoryFailed")
    class HandleInventoryFailed {

        @Test
        @DisplayName("updates PENDING order to FAILED and publishes order.failed with failedStep=INVENTORY")
        void pendingOrder_updatesToFailed_publishesOrderFailed() {
            UUID id = UUID.randomUUID();
            Order order = buildOrder(id, OrderStatus.PENDING);
            when(orderRepository.findById(id)).thenReturn(Optional.of(order));
            when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            sagaManager.handleInventoryFailed(inventoryFailedEvent(id.toString()));

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(captor.capture());
            Order saved = captor.getValue();

            assertThat(saved.getStatus()).isEqualTo(OrderStatus.FAILED);
            assertThat(saved.getSagaStatus()).isEqualTo(SagaStatus.FAILED);

            verify(orderService).publishOrderFailed(eq(saved), contains("OUT_OF_STOCK"), eq("INVENTORY"));
        }

        @ParameterizedTest(name = "skips save when order already in terminal state: {0}")
        @EnumSource(value = OrderStatus.class, names = {"CONFIRMED", "COMPLETED", "CANCELLED", "FAILED"})
        @DisplayName("skips save and publishOrderFailed when order already in final state")
        void finalState_skipsUpdate(OrderStatus status) {
            UUID id = UUID.randomUUID();
            Order order = buildOrder(id, status);
            when(orderRepository.findById(id)).thenReturn(Optional.of(order));

            sagaManager.handleInventoryFailed(inventoryFailedEvent(id.toString()));

            verify(orderRepository, never()).save(any());
            verify(orderService, never()).publishOrderFailed(any(), any(), any());
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when order does not exist")
        void orderNotFound_throwsResourceNotFoundException() {
            when(orderRepository.findById(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    sagaManager.handleInventoryFailed(inventoryFailedEvent(UUID.randomUUID().toString())))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── handlePaymentFailed ───────────────────────────────────────────────────

    @Nested
    @DisplayName("handlePaymentFailed")
    class HandlePaymentFailed {

        @Test
        @DisplayName("updates INVENTORY_RESERVED order to FAILED and publishes order.failed with failedStep=PAYMENT")
        void inventoryReservedOrder_updatesToFailed_publishesOrderFailedWithPaymentStep() {
            UUID id = UUID.randomUUID();
            Order order = buildOrder(id, OrderStatus.INVENTORY_RESERVED);
            when(orderRepository.findById(id)).thenReturn(Optional.of(order));
            when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            sagaManager.handlePaymentFailed(paymentFailedEvent(id.toString()));

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(captor.capture());
            Order saved = captor.getValue();

            assertThat(saved.getStatus()).isEqualTo(OrderStatus.FAILED);
            assertThat(saved.getSagaStatus()).isEqualTo(SagaStatus.FAILED);

            // PAYMENT failedStep triggers inventory-service to release reserved stock
            verify(orderService).publishOrderFailed(eq(saved), contains("INSUFFICIENT_WALLET_BALANCE"), eq("PAYMENT"));
        }

        @ParameterizedTest(name = "skips save when order already in terminal state: {0}")
        @EnumSource(value = OrderStatus.class, names = {"CONFIRMED", "COMPLETED", "CANCELLED", "FAILED"})
        @DisplayName("skips save and publishOrderFailed when order already in final state")
        void finalState_skipsUpdate(OrderStatus status) {
            UUID id = UUID.randomUUID();
            Order order = buildOrder(id, status);
            when(orderRepository.findById(id)).thenReturn(Optional.of(order));

            sagaManager.handlePaymentFailed(paymentFailedEvent(id.toString()));

            verify(orderRepository, never()).save(any());
            verify(orderService, never()).publishOrderFailed(any(), any(), any());
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when order does not exist")
        void orderNotFound_throwsResourceNotFoundException() {
            when(orderRepository.findById(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    sagaManager.handlePaymentFailed(paymentFailedEvent(UUID.randomUUID().toString())))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
