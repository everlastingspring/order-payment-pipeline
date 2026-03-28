package com.paymentplatform.inventoryservice.integration;

import com.paymentplatform.commonlib.constants.KafkaTopics;
import com.paymentplatform.commonlib.dto.MoneyDto;
import com.paymentplatform.commonlib.dto.OrderItemDto;
import com.paymentplatform.commonlib.dto.RestockRequest;
import com.paymentplatform.commonlib.enums.InventoryStatus;
import com.paymentplatform.commonlib.enums.PaymentMethod;
import com.paymentplatform.commonlib.events.OrderCancelledEvent;
import com.paymentplatform.commonlib.events.OrderCreatedEvent;
import com.paymentplatform.inventoryservice.application.service.InventoryService;
import com.paymentplatform.inventoryservice.domain.entity.Inventory;
import com.paymentplatform.inventoryservice.domain.entity.OutboxEvent;
import com.paymentplatform.inventoryservice.domain.entity.ReservationStatus;
import com.paymentplatform.inventoryservice.domain.repository.InventoryRepository;
import com.paymentplatform.inventoryservice.domain.repository.OutboxEventRepository;
import com.paymentplatform.inventoryservice.domain.repository.ReservationRecordRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.kafka.listener.auto-startup=false",
                "spring.kafka.admin.fail-fast=false",
                "management.tracing.enabled=false",
                "management.metrics.export.prometheus.enabled=false"
        }
)
@Transactional
@Testcontainers(disabledWithoutDocker = false)
class InventoryServiceIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("inventory_db")
                    .withUsername("test")
                    .withPassword("test");

    @Container
    private static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.4"));

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    private static final UUID MOUSE_PRODUCT_ID = UUID.fromString("a1b2c3d4-0000-0000-0000-000000000001");
    private static final UUID WEBCAM_PRODUCT_ID = UUID.fromString("a1b2c3d4-0000-0000-0000-000000000005");
    private static final String ORDER_ID = "order-it-001";

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private ReservationRecordRepository reservationRecordRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    void restockInventory_updatesPersistedQuantityAndStatus() {
        Inventory before = inventoryRepository.findByProductIdWithProduct(WEBCAM_PRODUCT_ID).orElseThrow();
        assertThat(before.getAvailableQuantity()).isZero();
        assertThat(before.getStatus()).isEqualTo(InventoryStatus.OUT_OF_STOCK);

        inventoryService.restockInventory(WEBCAM_PRODUCT_ID, RestockRequest.builder()
                .quantity(25)
                .reason("Integration replenishment")
                .build());

        Inventory after = inventoryRepository.findByProductIdWithProduct(WEBCAM_PRODUCT_ID).orElseThrow();

        assertThat(after.getAvailableQuantity()).isEqualTo(25);
        assertThat(after.getReservedQuantity()).isZero();
        assertThat(after.getStatus()).isEqualTo(InventoryStatus.AVAILABLE);
        assertThat(inventoryService.getLowStockInventory(30))
                .extracting(item -> item.getProductId())
                .contains(WEBCAM_PRODUCT_ID.toString());
    }

    @Test
    void reserveInventory_persistsReservationAndOutboxEvent() {
        inventoryService.reserveInventory(orderCreatedEvent(ORDER_ID, 3));

        Inventory inventory = inventoryRepository.findByProductIdWithProduct(MOUSE_PRODUCT_ID).orElseThrow();
        List<OutboxEvent> outboxEvents = outboxEventRepository.findAll();

        assertThat(inventory.getAvailableQuantity()).isEqualTo(97);
        assertThat(inventory.getReservedQuantity()).isEqualTo(3);
        assertThat(reservationRecordRepository.findByOrderIdAndStatus(ORDER_ID, ReservationStatus.ACTIVE))
                .hasSize(1);
        assertThat(outboxEvents).singleElement().satisfies(event -> {
            assertThat(event.getTopic()).isEqualTo(KafkaTopics.INVENTORY_RESERVED);
            assertThat(event.getAggregateId()).isEqualTo(ORDER_ID);
            assertThat(event.getPayload()).contains(ORDER_ID);
        });
    }

    @Test
    void releaseInventory_restoresStockAndMarksReservationReleased() {
        inventoryService.reserveInventory(orderCreatedEvent(ORDER_ID, 2));

        inventoryService.releaseInventory(OrderCancelledEvent.builder()
                .orderId(ORDER_ID)
                .customerId("customer-it-001")
                .cancellationReason("Integration cancellation")
                .build());

        Inventory inventory = inventoryRepository.findByProductIdWithProduct(MOUSE_PRODUCT_ID).orElseThrow();

        assertThat(inventory.getAvailableQuantity()).isEqualTo(100);
        assertThat(inventory.getReservedQuantity()).isZero();
        assertThat(reservationRecordRepository.findByOrderIdAndStatus(ORDER_ID, ReservationStatus.RELEASED))
                .hasSize(1);
    }

    private static OrderCreatedEvent orderCreatedEvent(String orderId, int quantity) {
        return OrderCreatedEvent.builder()
                .orderId(orderId)
                .customerId("customer-it-001")
                .customerEmail("customer-it@example.com")
                .paymentMethod(PaymentMethod.WALLET)
                .totalAmount(MoneyDto.builder().amount(new BigDecimal("799.00")).currency("INR").build())
                .items(List.of(OrderItemDto.builder()
                        .productId(MOUSE_PRODUCT_ID.toString())
                        .quantity(quantity)
                        .unitPrice(MoneyDto.builder()
                                .amount(new BigDecimal("799.00"))
                                .currency("INR")
                                .build())
                        .subtotal(MoneyDto.builder()
                                .amount(new BigDecimal("799.00").multiply(BigDecimal.valueOf(quantity)))
                                .currency("INR")
                                .build())
                        .build()))
                .shippingAddress("42 Integration Street")
                .build();
    }
}
