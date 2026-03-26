package com.paymentplatform.orderservice.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.paymentplatform.commonlib.dto.MoneyDto;
import com.paymentplatform.commonlib.dto.OrderDto;
import com.paymentplatform.commonlib.enums.OrderStatus;
import com.paymentplatform.commonlib.enums.PaymentMethod;
import com.paymentplatform.commonlib.enums.SagaStatus;
import com.paymentplatform.commonlib.exception.InvalidOrderStateException;
import com.paymentplatform.commonlib.exception.ResourceNotFoundException;
import com.paymentplatform.orderservice.api.dto.CreateOrderRequest;
import com.paymentplatform.orderservice.application.mapper.OrderMapper;
import com.paymentplatform.orderservice.domain.entity.Order;
import com.paymentplatform.orderservice.domain.entity.OutboxEvent;
import com.paymentplatform.orderservice.domain.repository.OrderRepository;
import com.paymentplatform.orderservice.domain.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService")
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private OrderMapper orderMapper;

    private ObjectMapper objectMapper;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        orderService = new OrderService(orderRepository, outboxEventRepository, orderMapper, objectMapper);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Order buildOrder(UUID id, OrderStatus status, boolean paymentCompleted) {
        Order order = Order.builder()
                .customerId("customer-001")
                .customerEmail("test@example.com")
                .status(status)
                .sagaStatus(SagaStatus.STARTED)
                .paymentMethod(PaymentMethod.WALLET)
                .totalAmount(new BigDecimal("4097.00"))
                .currency("INR")
                .shippingAddress("123 Test St")
                .paymentCompleted(paymentCompleted)
                .inventoryReserved(false)
                .items(new ArrayList<>())
                .build();
        setId(order, id);
        return order;
    }

    private void setId(Object entity, UUID id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private OrderDto stubOrderDto(UUID id) {
        return OrderDto.builder()
                .orderId(id.toString())
                .customerId("customer-001")
                .status(OrderStatus.PENDING)
                .totalAmount(MoneyDto.builder().amount(new BigDecimal("4097.00")).currency("INR").build())
                .items(List.of())
                .build();
    }

    private CreateOrderRequest buildCreateRequest() {
        return CreateOrderRequest.builder()
                .customerId("customer-001")
                .customerEmail("test@example.com")
                .paymentMethod(PaymentMethod.WALLET)
                .shippingAddress("123 Test St")
                .items(List.of(
                        CreateOrderRequest.OrderItemRequest.builder()
                                .productId("a1b2c3d4-0000-0000-0000-000000000001")
                                .productName("Wireless Mouse")
                                .sku("MOUSE-WL-001")
                                .quantity(2)
                                .unitPrice(new BigDecimal("799.00"))
                                .currency("INR")
                                .build(),
                        CreateOrderRequest.OrderItemRequest.builder()
                                .productId("a1b2c3d4-0000-0000-0000-000000000002")
                                .productName("Mechanical Keyboard")
                                .sku("KB-MECH-001")
                                .quantity(1)
                                .unitPrice(new BigDecimal("2499.00"))
                                .currency("INR")
                                .build()
                ))
                .build();
    }

    // ── createOrder ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createOrder")
    class CreateOrder {

        @Test
        @DisplayName("saves order with PENDING status, correct total, and STARTED saga status")
        void happyPath_savesOrderWithCorrectState() {
            UUID id = UUID.randomUUID();
            Order saved = buildOrder(id, OrderStatus.PENDING, false);
            when(orderRepository.save(any())).thenReturn(saved);
            when(orderMapper.toDto(any())).thenReturn(stubOrderDto(id));

            orderService.createOrder(buildCreateRequest());

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(captor.capture());
            Order captured = captor.getValue();

            assertThat(captured.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(captured.getSagaStatus()).isEqualTo(SagaStatus.STARTED);
            assertThat(captured.isPaymentCompleted()).isFalse();
            assertThat(captured.isInventoryReserved()).isFalse();
            assertThat(captured.getCustomerId()).isEqualTo("customer-001");
            assertThat(captured.getPaymentMethod()).isEqualTo(PaymentMethod.WALLET);
        }

        @Test
        @DisplayName("calculates total as sum of (quantity × unitPrice) across all items")
        void calculatesTotalAmountCorrectly() {
            UUID id = UUID.randomUUID();
            Order saved = buildOrder(id, OrderStatus.PENDING, false);
            when(orderRepository.save(any())).thenReturn(saved);
            when(orderMapper.toDto(any())).thenReturn(stubOrderDto(id));

            orderService.createOrder(buildCreateRequest());

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(captor.capture());
            // 2 × 799 = 1598,  1 × 2499 = 2499,  total = 4097
            assertThat(captor.getValue().getTotalAmount()).isEqualByComparingTo("4097.00");
            assertThat(captor.getValue().getItems()).hasSize(2);
        }

        @Test
        @DisplayName("saves outbox event for order.created topic with orderId in payload")
        void savesOutboxEventForOrderCreated() {
            UUID id = UUID.randomUUID();
            Order saved = buildOrder(id, OrderStatus.PENDING, false);
            when(orderRepository.save(any())).thenReturn(saved);
            when(orderMapper.toDto(any())).thenReturn(stubOrderDto(id));

            orderService.createOrder(buildCreateRequest());

            ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxEventRepository).save(captor.capture());
            OutboxEvent outbox = captor.getValue();

            assertThat(outbox.getTopic()).isEqualTo("order.created");
            assertThat(outbox.getAggregateType()).isEqualTo("Order");
            assertThat(outbox.getAggregateId()).isEqualTo(id.toString());
            assertThat(outbox.getPayload()).contains("orderId");
        }

        @Test
        @DisplayName("returns the mapped DTO from orderMapper")
        void returnsMappedDto() {
            UUID id = UUID.randomUUID();
            Order saved = buildOrder(id, OrderStatus.PENDING, false);
            OrderDto expectedDto = stubOrderDto(id);
            when(orderRepository.save(any())).thenReturn(saved);
            when(orderMapper.toDto(any())).thenReturn(expectedDto);

            OrderDto result = orderService.createOrder(buildCreateRequest());

            assertThat(result).isEqualTo(expectedDto);
        }

        @Test
        @DisplayName("throws RuntimeException when ObjectMapper fails to serialize event")
        void serializationFailure_throwsRuntimeException() throws JsonProcessingException {
            ObjectMapper spyMapper = spy(objectMapper);
            doThrow(new JsonProcessingException("fail") {}).when(spyMapper).writeValueAsString(any());
            OrderService serviceWithSpyMapper = new OrderService(
                    orderRepository, outboxEventRepository, orderMapper, spyMapper);

            UUID id = UUID.randomUUID();
            Order saved = buildOrder(id, OrderStatus.PENDING, false);
            when(orderRepository.save(any())).thenReturn(saved);
            when(orderMapper.toDto(any())).thenReturn(stubOrderDto(id));

            assertThatThrownBy(() -> serviceWithSpyMapper.createOrder(buildCreateRequest()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to serialize");
        }
    }

    // ── getOrder ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getOrder")
    class GetOrder {

        @Test
        @DisplayName("returns mapped DTO when order found")
        void found_returnsMappedDto() {
            UUID id = UUID.randomUUID();
            Order order = buildOrder(id, OrderStatus.PENDING, false);
            OrderDto dto = stubOrderDto(id);
            when(orderRepository.findById(id)).thenReturn(Optional.of(order));
            when(orderMapper.toDto(order)).thenReturn(dto);

            OrderDto result = orderService.getOrder(id);

            assertThat(result).isEqualTo(dto);
            verify(orderMapper).toDto(order);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when order does not exist")
        void notFound_throwsResourceNotFoundException() {
            UUID id = UUID.randomUUID();
            when(orderRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.getOrder(id))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── getOrdersByCustomer ───────────────────────────────────────────────────

    @Nested
    @DisplayName("getOrdersByCustomer")
    class GetOrdersByCustomer {

        @Test
        @DisplayName("delegates to repository and maps results")
        void returnsPagedResults() {
            UUID id = UUID.randomUUID();
            Order order = buildOrder(id, OrderStatus.CONFIRMED, true);
            OrderDto dto = stubOrderDto(id);
            Pageable pageable = PageRequest.of(0, 10);
            Page<Order> page = new PageImpl<>(List.of(order));

            when(orderRepository.findByCustomerIdOrderByCreatedAtDesc("customer-001", pageable))
                    .thenReturn(page);
            when(orderMapper.toDto(order)).thenReturn(dto);

            Page<OrderDto> result = orderService.getOrdersByCustomer("customer-001", pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0)).isEqualTo(dto);
        }

        @Test
        @DisplayName("returns empty page when customer has no orders")
        void noOrders_returnsEmptyPage() {
            Pageable pageable = PageRequest.of(0, 10);
            when(orderRepository.findByCustomerIdOrderByCreatedAtDesc("customer-999", pageable))
                    .thenReturn(Page.empty());

            Page<OrderDto> result = orderService.getOrdersByCustomer("customer-999", pageable);

            assertThat(result.getContent()).isEmpty();
        }
    }

    // ── cancelOrder ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("cancelOrder")
    class CancelOrder {

        @Test
        @DisplayName("PENDING order cancels without wallet refund (refundAmount=0)")
        void fromPending_cancelsWithZeroRefund() {
            UUID id = UUID.randomUUID();
            Order order = buildOrder(id, OrderStatus.PENDING, false);
            when(orderRepository.findById(id)).thenReturn(Optional.of(order));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderMapper.toDto(any())).thenReturn(stubOrderDto(id));

            orderService.cancelOrder(id, "Customer request");

            ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxEventRepository).save(captor.capture());
            String payload = captor.getValue().getPayload();
            assertThat(payload).contains("\"amount\":0");
            assertThat(payload).contains("\"previousStatus\":\"PENDING\"");
        }

        @Test
        @DisplayName("INVENTORY_RESERVED order cancels without wallet refund")
        void fromInventoryReserved_cancelsWithZeroRefund() {
            UUID id = UUID.randomUUID();
            Order order = buildOrder(id, OrderStatus.INVENTORY_RESERVED, false);
            when(orderRepository.findById(id)).thenReturn(Optional.of(order));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderMapper.toDto(any())).thenReturn(stubOrderDto(id));

            orderService.cancelOrder(id, "Customer request");

            ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxEventRepository).save(captor.capture());
            String payload = captor.getValue().getPayload();
            assertThat(payload).contains("\"amount\":0");
            assertThat(payload).contains("\"previousStatus\":\"INVENTORY_RESERVED\"");
        }

        @Test
        @DisplayName("CONFIRMED order cancels with full totalAmount as wallet refund")
        void fromConfirmed_cancelsWithFullRefund() {
            UUID id = UUID.randomUUID();
            Order order = buildOrder(id, OrderStatus.CONFIRMED, true);
            when(orderRepository.findById(id)).thenReturn(Optional.of(order));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderMapper.toDto(any())).thenReturn(stubOrderDto(id));

            orderService.cancelOrder(id, "Customer request");

            ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxEventRepository).save(captor.capture());
            String payload = captor.getValue().getPayload();
            assertThat(payload).contains("4097");
            assertThat(payload).doesNotContain("\"amount\":0");
        }

        @Test
        @DisplayName("previousStatus in outbox event must be the pre-mutation status, not CANCELLED (regression)")
        void previousStatus_isPreMutationStatus_notCancelled() {
            UUID id = UUID.randomUUID();
            Order order = buildOrder(id, OrderStatus.CONFIRMED, true);
            when(orderRepository.findById(id)).thenReturn(Optional.of(order));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderMapper.toDto(any())).thenReturn(stubOrderDto(id));

            orderService.cancelOrder(id, "Regression test");

            ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxEventRepository).save(captor.capture());
            assertThat(captor.getValue().getPayload())
                    .contains("\"previousStatus\":\"CONFIRMED\"")
                    .doesNotContain("\"previousStatus\":\"CANCELLED\"");
        }

        @Test
        @DisplayName("sets order status=CANCELLED and sagaStatus=COMPENSATING")
        void setsCorrectStatusAndSagaStatus() {
            UUID id = UUID.randomUUID();
            Order order = buildOrder(id, OrderStatus.PENDING, false);
            when(orderRepository.findById(id)).thenReturn(Optional.of(order));
            when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(orderMapper.toDto(any())).thenReturn(stubOrderDto(id));

            orderService.cancelOrder(id, "Test");

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(captor.getValue().getSagaStatus()).isEqualTo(SagaStatus.COMPENSATING);
        }

        @Test
        @DisplayName("throws InvalidOrderStateException and does not save when order is CANCELLED")
        void alreadyCancelled_throws_noSave() {
            UUID id = UUID.randomUUID();
            Order order = buildOrder(id, OrderStatus.CANCELLED, false);
            when(orderRepository.findById(id)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.cancelOrder(id, "double cancel"))
                    .isInstanceOf(InvalidOrderStateException.class);
            verify(orderRepository, never()).save(any());
            verify(outboxEventRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws InvalidOrderStateException and does not save when order is FAILED")
        void alreadyFailed_throws_noSave() {
            UUID id = UUID.randomUUID();
            Order order = buildOrder(id, OrderStatus.FAILED, false);
            when(orderRepository.findById(id)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.cancelOrder(id, "cancel failed"))
                    .isInstanceOf(InvalidOrderStateException.class);
            verify(orderRepository, never()).save(any());
            verify(outboxEventRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when order does not exist")
        void orderNotFound_throwsResourceNotFoundException() {
            UUID id = UUID.randomUUID();
            when(orderRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.cancelOrder(id, "cancel"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── publishOrderCompleted ─────────────────────────────────────────────────

    @Nested
    @DisplayName("publishOrderCompleted")
    class PublishOrderCompleted {

        @Test
        @DisplayName("saves outbox event for order.completed topic with orderId and totalAmount")
        void savesOrderCompletedOutbox() {
            UUID id = UUID.randomUUID();
            Order order = buildOrder(id, OrderStatus.CONFIRMED, true);

            orderService.publishOrderCompleted(order);

            ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxEventRepository).save(captor.capture());
            OutboxEvent outbox = captor.getValue();
            assertThat(outbox.getTopic()).isEqualTo("order.completed");
            assertThat(outbox.getAggregateId()).isEqualTo(id.toString());
            assertThat(outbox.getPayload()).contains("orderId");
            assertThat(outbox.getPayload()).contains("4097");
        }
    }

    // ── publishOrderFailed ────────────────────────────────────────────────────

    @Nested
    @DisplayName("publishOrderFailed")
    class PublishOrderFailed {

        @Test
        @DisplayName("saves outbox event for order.failed topic with failedStep in payload")
        void savesOrderFailedOutboxWithFailedStep() {
            UUID id = UUID.randomUUID();
            Order order = buildOrder(id, OrderStatus.FAILED, false);

            orderService.publishOrderFailed(order, "Inventory unavailable: OUT_OF_STOCK", "INVENTORY");

            ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxEventRepository).save(captor.capture());
            OutboxEvent outbox = captor.getValue();
            assertThat(outbox.getTopic()).isEqualTo("order.failed");
            assertThat(outbox.getPayload()).contains("INVENTORY");
            assertThat(outbox.getPayload()).contains("failedStep");
        }

        @Test
        @DisplayName("saves order.failed event with PAYMENT failedStep for payment compensation")
        void paymentFailedStep_savesCorrectOutbox() {
            UUID id = UUID.randomUUID();
            Order order = buildOrder(id, OrderStatus.FAILED, false);

            orderService.publishOrderFailed(order, "Payment declined", "PAYMENT");

            ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxEventRepository).save(captor.capture());
            assertThat(captor.getValue().getPayload()).contains("PAYMENT");
        }
    }

    // ── publishOrderCancelled (direct) ────────────────────────────────────────

    @Nested
    @DisplayName("publishOrderCancelled (direct)")
    class PublishOrderCancelledDirect {

        @Test
        @DisplayName("saves order.cancelled outbox event with explicit previousStatus and zero refund")
        void zeroRefund_savesOutboxWithPreviousStatus() {
            UUID id = UUID.randomUUID();
            Order order = buildOrder(id, OrderStatus.CANCELLED, false);   // already mutated
            // Pass PENDING as previousStatus (as cancelOrder() would)
            orderService.publishOrderCancelled(order, "Test", "CUSTOMER", OrderStatus.PENDING);

            ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxEventRepository).save(captor.capture());
            String payload = captor.getValue().getPayload();
            assertThat(payload).contains("\"previousStatus\":\"PENDING\"");
            assertThat(payload).contains("\"amount\":0");
        }

        @Test
        @DisplayName("saves order.cancelled outbox event with full refund when paymentCompleted=true")
        void fullRefund_savesOutboxWithRefundAmount() {
            UUID id = UUID.randomUUID();
            Order order = buildOrder(id, OrderStatus.CANCELLED, true);    // payment was taken
            orderService.publishOrderCancelled(order, "Test", "CUSTOMER", OrderStatus.CONFIRMED);

            ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxEventRepository).save(captor.capture());
            String payload = captor.getValue().getPayload();
            assertThat(payload).contains("4097");
            assertThat(payload).contains("\"previousStatus\":\"CONFIRMED\"");
        }
    }
}
