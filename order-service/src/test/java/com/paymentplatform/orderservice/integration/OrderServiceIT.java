package com.paymentplatform.orderservice.integration;

import com.paymentplatform.commonlib.constants.KafkaTopics;
import com.paymentplatform.commonlib.dto.MoneyDto;
import com.paymentplatform.commonlib.dto.OrderDto;
import com.paymentplatform.commonlib.enums.OrderStatus;
import com.paymentplatform.commonlib.enums.PaymentMethod;
import com.paymentplatform.commonlib.enums.SagaStatus;
import com.paymentplatform.commonlib.events.InventoryReservedEvent;
import com.paymentplatform.commonlib.events.PaymentCompletedEvent;
import com.paymentplatform.commonlib.events.PaymentFailedEvent;
import com.paymentplatform.orderservice.api.dto.CreateOrderRequest;
import com.paymentplatform.orderservice.application.service.OrderService;
import com.paymentplatform.orderservice.domain.entity.Order;
import com.paymentplatform.orderservice.domain.entity.OutboxEvent;
import com.paymentplatform.orderservice.domain.repository.OrderRepository;
import com.paymentplatform.orderservice.domain.repository.OutboxEventRepository;
import com.paymentplatform.orderservice.infrastructure.kafka.InventoryEventConsumer;
import com.paymentplatform.orderservice.infrastructure.kafka.PaymentEventConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.kafka.listener.auto-startup=false",
                "spring.kafka.admin.fail-fast=false",
                "spring.task.scheduling.enabled=false",
                "management.tracing.enabled=false",
                "management.metrics.export.prometheus.enabled=false"
        }
)
@Testcontainers
class OrderServiceIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("order_db")
                    .withUsername("test")
                    .withPassword("test");

    @Container
    private static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.4"));

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
    }

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private InventoryEventConsumer inventoryEventConsumer;

    @Autowired
    private PaymentEventConsumer paymentEventConsumer;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
        orderRepository.deleteAll();
    }

    @Test
    void createOrder_thenInventoryReservedAndPaymentCompleted_confirmsOrderAndWritesOutbox() throws Exception {
        OrderDto created = orderService.createOrder(createOrderRequest());
        String orderId = created.getOrderId();

        InventoryReservedEvent inventoryReserved = InventoryReservedEvent.builder()
                .orderId(orderId)
                .reservationId("reservation-it-001")
                .customerId("customer-it-001")
                .customerEmail("customer-it@example.com")
                .paymentMethod(PaymentMethod.WALLET)
                .totalAmount(MoneyDto.builder().amount(new BigDecimal("4097.00")).currency("INR").build())
                .reservedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .build();

        Acknowledgment inventoryAck = mock(Acknowledgment.class);
        inventoryEventConsumer.onInventoryReserved(objectMapper.writeValueAsString(inventoryReserved), inventoryAck);
        verify(inventoryAck).acknowledge();

        PaymentCompletedEvent paymentCompleted = PaymentCompletedEvent.builder()
                .paymentId("payment-it-001")
                .orderId(orderId)
                .customerId("customer-it-001")
                .amount(MoneyDto.builder().amount(new BigDecimal("4097.00")).currency("INR").build())
                .paymentMethod(PaymentMethod.WALLET)
                .transactionReference("WALLET-TXN-001")
                .completedAt(Instant.now())
                .build();

        Acknowledgment paymentAck = mock(Acknowledgment.class);
        paymentEventConsumer.onPaymentCompleted(objectMapper.writeValueAsString(paymentCompleted), paymentAck);
        verify(paymentAck).acknowledge();

        Order order = orderRepository.findById(UUID.fromString(orderId)).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getSagaStatus()).isEqualTo(SagaStatus.COMPLETED);
        assertThat(order.isInventoryReserved()).isTrue();
        assertThat(order.isPaymentCompleted()).isTrue();
        assertThat(order.getPaymentId()).isEqualTo("payment-it-001");

        List<String> topics = outboxEventRepository.findAll().stream()
                .filter(event -> event.getAggregateId().equals(orderId))
                .map(OutboxEvent::getTopic)
                .toList();
        assertThat(topics).contains(KafkaTopics.ORDER_CREATED, KafkaTopics.ORDER_COMPLETED);
    }

    @Test
    void paymentFailedEvent_marksOrderFailedAndWritesOrderFailedOutbox() throws Exception {
        OrderDto created = orderService.createOrder(createOrderRequest());
        String orderId = created.getOrderId();

        InventoryReservedEvent inventoryReserved = InventoryReservedEvent.builder()
                .orderId(orderId)
                .reservationId("reservation-it-002")
                .customerId("customer-it-001")
                .customerEmail("customer-it@example.com")
                .paymentMethod(PaymentMethod.WALLET)
                .totalAmount(MoneyDto.builder().amount(new BigDecimal("4097.00")).currency("INR").build())
                .reservedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .build();
        inventoryEventConsumer.onInventoryReserved(
                objectMapper.writeValueAsString(inventoryReserved),
                mock(Acknowledgment.class)
        );

        PaymentFailedEvent paymentFailed = PaymentFailedEvent.builder()
                .paymentId("payment-it-fail-001")
                .orderId(orderId)
                .customerId("customer-it-001")
                .attemptedAmount(MoneyDto.builder().amount(new BigDecimal("4097.00")).currency("INR").build())
                .paymentMethod(PaymentMethod.WALLET)
                .failureReason("INSUFFICIENT_WALLET_BALANCE")
                .failureCode("INSUFFICIENT_WALLET_BALANCE")
                .failedAt(Instant.now())
                .build();

        Acknowledgment paymentAck = mock(Acknowledgment.class);
        paymentEventConsumer.onPaymentFailed(objectMapper.writeValueAsString(paymentFailed), paymentAck);
        verify(paymentAck).acknowledge();

        Order order = orderRepository.findById(UUID.fromString(orderId)).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(order.getSagaStatus()).isEqualTo(SagaStatus.FAILED);
        assertThat(order.isInventoryReserved()).isTrue();
        assertThat(order.isPaymentCompleted()).isFalse();

        List<String> topics = outboxEventRepository.findAll().stream()
                .filter(event -> event.getAggregateId().equals(orderId))
                .map(OutboxEvent::getTopic)
                .toList();
        assertThat(topics).contains(KafkaTopics.ORDER_CREATED, KafkaTopics.ORDER_FAILED);
    }

    @Test
    void inventoryFailedEvent_marksOrderFailedWithoutPaymentAttempt() throws Exception {
        OrderDto created = orderService.createOrder(createOrderRequest());
        String orderId = created.getOrderId();

        com.paymentplatform.commonlib.events.InventoryFailedEvent inventoryFailed =
                com.paymentplatform.commonlib.events.InventoryFailedEvent.builder()
                .orderId(orderId)
                .failedItems(List.of(
                        com.paymentplatform.commonlib.events.InventoryFailedEvent.FailedItem.builder()
                                .productId("a1b2c3d4-0000-0000-0000-000000000001")
                                .requestedQuantity(2)
                                .availableQuantity(0)
                                .build()
                ))
                .failureReason("OUT_OF_STOCK")
                .failedAt(Instant.now())
                .build();

        Acknowledgment inventoryAck = mock(Acknowledgment.class);
        inventoryEventConsumer.onInventoryFailed(objectMapper.writeValueAsString(inventoryFailed), inventoryAck);
        verify(inventoryAck).acknowledge();

        Order order = orderRepository.findById(UUID.fromString(orderId)).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(order.getSagaStatus()).isEqualTo(SagaStatus.FAILED);
        assertThat(order.isInventoryReserved()).isFalse();
        assertThat(order.isPaymentCompleted()).isFalse();
    }

    @Test
    void duplicatePaymentCompletedEvent_isIdempotent() throws Exception {
        OrderDto created = orderService.createOrder(createOrderRequest());
        String orderId = created.getOrderId();

        // First, reserve inventory
        InventoryReservedEvent inventoryReserved = InventoryReservedEvent.builder()
                .orderId(orderId)
                .reservationId("reservation-it-003")
                .customerId("customer-it-001")
                .customerEmail("customer-it@example.com")
                .paymentMethod(PaymentMethod.WALLET)
                .totalAmount(MoneyDto.builder().amount(new BigDecimal("4097.00")).currency("INR").build())
                .reservedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .build();
        inventoryEventConsumer.onInventoryReserved(objectMapper.writeValueAsString(inventoryReserved), mock(Acknowledgment.class));

        PaymentCompletedEvent paymentCompleted = PaymentCompletedEvent.builder()
                .paymentId("payment-it-003")
                .orderId(orderId)
                .customerId("customer-it-001")
                .amount(MoneyDto.builder().amount(new BigDecimal("4097.00")).currency("INR").build())
                .paymentMethod(PaymentMethod.WALLET)
                .transactionReference("WALLET-TXN-003")
                .completedAt(Instant.now())
                .build();

        // Send payment completed twice
        paymentEventConsumer.onPaymentCompleted(objectMapper.writeValueAsString(paymentCompleted), mock(Acknowledgment.class));
        paymentEventConsumer.onPaymentCompleted(objectMapper.writeValueAsString(paymentCompleted), mock(Acknowledgment.class));

        // Order should be CONFIRMED only once
        Order order = orderRepository.findById(UUID.fromString(orderId)).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.isPaymentCompleted()).isTrue();

        // Verify only one order.completed event in outbox
        List<OutboxEvent> completedEvents = outboxEventRepository.findAll().stream()
                .filter(e -> e.getAggregateId().equals(orderId) && e.getTopic().equals(KafkaTopics.ORDER_COMPLETED))
                .toList();
        assertThat(completedEvents).hasSize(1);
    }

    private static CreateOrderRequest createOrderRequest() {
        return CreateOrderRequest.builder()
                .customerId("customer-it-001")
                .customerEmail("customer-it@example.com")
                .paymentMethod(PaymentMethod.WALLET)
                .shippingAddress("42 Integration Street")
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
}
