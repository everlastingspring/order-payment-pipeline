package com.paymentplatform.inventoryservice.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.paymentplatform.commonlib.dto.InventoryDto;
import com.paymentplatform.commonlib.dto.MoneyDto;
import com.paymentplatform.commonlib.dto.OrderItemDto;
import com.paymentplatform.commonlib.dto.RestockRequest;
import com.paymentplatform.commonlib.enums.InventoryStatus;
import com.paymentplatform.commonlib.enums.PaymentMethod;
import com.paymentplatform.commonlib.events.OrderCancelledEvent;
import com.paymentplatform.commonlib.events.OrderCreatedEvent;
import com.paymentplatform.commonlib.exception.ResourceNotFoundException;
import com.paymentplatform.inventoryservice.application.mapper.InventoryMapper;
import com.paymentplatform.inventoryservice.domain.entity.Inventory;
import com.paymentplatform.inventoryservice.domain.entity.OutboxEvent;
import com.paymentplatform.inventoryservice.domain.entity.Product;
import com.paymentplatform.inventoryservice.domain.entity.ReservationRecord;
import com.paymentplatform.inventoryservice.domain.entity.ReservationStatus;
import com.paymentplatform.inventoryservice.domain.repository.InventoryRepository;
import com.paymentplatform.inventoryservice.domain.repository.OutboxEventRepository;
import com.paymentplatform.inventoryservice.domain.repository.ReservationRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryService")
class InventoryServiceTest {

    @Mock private InventoryRepository inventoryRepository;
    @Mock private ReservationRecordRepository reservationRecordRepository;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private InventoryMapper inventoryMapper;

    private ObjectMapper objectMapper;
    private InventoryService inventoryService;

    private static final UUID PRODUCT_1 = UUID.fromString("a1b2c3d4-0000-0000-0000-000000000001");
    private static final UUID PRODUCT_2 = UUID.fromString("a1b2c3d4-0000-0000-0000-000000000002");
    private static final String ORDER_ID = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        inventoryService = new InventoryService(
                inventoryRepository, reservationRecordRepository,
                outboxEventRepository, inventoryMapper, objectMapper);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Product buildProduct(UUID id, String name, String sku) {
        Product product = Product.builder()
                .name(name)
                .sku(sku)
                .price(new BigDecimal("799.00"))
                .currency("INR")
                .build();
        setField(product, "id", id);
        return product;
    }

    private Inventory buildInventory(UUID productId, int available, int reserved) {
        Product product = buildProduct(productId, "Product", "SKU-001");
        Inventory inventory = Inventory.builder()
                .product(product)
                .availableQuantity(available)
                .reservedQuantity(reserved)
                .warehouseId("WH-001")
                .status(available > 0 ? InventoryStatus.AVAILABLE : InventoryStatus.OUT_OF_STOCK)
                .build();
        setField(inventory, "id", UUID.randomUUID());
        return inventory;
    }

    private OrderCreatedEvent buildOrderCreatedEvent(List<OrderItemDto> items) {
        return OrderCreatedEvent.builder()
                .orderId(ORDER_ID)
                .customerId("customer-001")
                .customerEmail("test@example.com")
                .totalAmount(MoneyDto.builder().amount(new BigDecimal("4097.00")).currency("INR").build())
                .paymentMethod(PaymentMethod.WALLET)
                .items(items)
                .build();
    }

    private OrderItemDto buildItem(UUID productId, int quantity) {
        return OrderItemDto.builder()
                .productId(productId.toString())
                .productName("Product")
                .sku("SKU-001")
                .quantity(quantity)
                .unitPrice(MoneyDto.builder().amount(new BigDecimal("799.00")).currency("INR").build())
                .build();
    }

    private void setField(Object target, String name, Object value) {
        try {
            var f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ── reserveInventory ───────────────────────────────────────────────────

    @Nested
    @DisplayName("reserveInventory")
    class ReserveInventory {

        @Test
        @DisplayName("happy path: reserves all items, saves reservation records, publishes inventory.reserved")
        void happyPath_reservesAllItems() {
            Inventory inv1 = buildInventory(PRODUCT_1, 100, 0);
            Inventory inv2 = buildInventory(PRODUCT_2, 50, 0);
            when(inventoryRepository.findByProductIdWithProduct(PRODUCT_1))
                    .thenReturn(Optional.of(inv1));
            when(inventoryRepository.findByProductIdWithProduct(PRODUCT_2))
                    .thenReturn(Optional.of(inv2));

            OrderCreatedEvent event = buildOrderCreatedEvent(List.of(
                    buildItem(PRODUCT_1, 2),
                    buildItem(PRODUCT_2, 1)
            ));

            inventoryService.reserveInventory(event);

            // inventory.save called for each item in second pass
            verify(inventoryRepository, times(2)).save(any(Inventory.class));
            // reservation records saved
            verify(reservationRecordRepository, times(2)).save(any(ReservationRecord.class));
            // outbox event for inventory.reserved
            ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxEventRepository).save(captor.capture());
            assertThat(captor.getValue().getTopic()).isEqualTo("inventory.reserved");
            assertThat(captor.getValue().getPayload()).contains(ORDER_ID);
        }

        @Test
        @DisplayName("reserves correct quantities: available decreases, reserved increases")
        void reservesCorrectQuantities() {
            Inventory inv = buildInventory(PRODUCT_1, 100, 0);
            when(inventoryRepository.findByProductIdWithProduct(PRODUCT_1))
                    .thenReturn(Optional.of(inv));

            OrderCreatedEvent event = buildOrderCreatedEvent(List.of(
                    buildItem(PRODUCT_1, 3)
            ));

            inventoryService.reserveInventory(event);

            assertThat(inv.getAvailableQuantity()).isEqualTo(97);
            assertThat(inv.getReservedQuantity()).isEqualTo(3);
        }

        @Test
        @DisplayName("insufficient stock: publishes inventory.failed, no reservations saved")
        void insufficientStock_publishesFailedEvent() {
            Inventory inv = buildInventory(PRODUCT_1, 1, 0);
            when(inventoryRepository.findByProductIdWithProduct(PRODUCT_1))
                    .thenReturn(Optional.of(inv));

            OrderCreatedEvent event = buildOrderCreatedEvent(List.of(
                    buildItem(PRODUCT_1, 5)
            ));

            inventoryService.reserveInventory(event);

            // No reservation records saved
            verify(reservationRecordRepository, never()).save(any());
            // inventory.failed outbox event
            ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxEventRepository).save(captor.capture());
            assertThat(captor.getValue().getTopic()).isEqualTo("inventory.failed");
            assertThat(captor.getValue().getPayload()).contains("requestedQuantity");
        }

        @Test
        @DisplayName("product not found: publishes inventory.failed with availableQuantity=0")
        void productNotFound_publishesFailedEvent() {
            when(inventoryRepository.findByProductIdWithProduct(PRODUCT_1))
                    .thenReturn(Optional.empty());

            OrderCreatedEvent event = buildOrderCreatedEvent(List.of(
                    buildItem(PRODUCT_1, 1)
            ));

            inventoryService.reserveInventory(event);

            verify(reservationRecordRepository, never()).save(any());
            ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxEventRepository).save(captor.capture());
            assertThat(captor.getValue().getTopic()).isEqualTo("inventory.failed");
            assertThat(captor.getValue().getPayload()).contains("\"availableQuantity\":0");
        }

        @Test
        @DisplayName("mixed items: one available, one out-of-stock → entire reservation fails")
        void mixedAvailability_entireReservationFails() {
            Inventory inv1 = buildInventory(PRODUCT_1, 100, 0);
            when(inventoryRepository.findByProductIdWithProduct(PRODUCT_1))
                    .thenReturn(Optional.of(inv1));
            when(inventoryRepository.findByProductIdWithProduct(PRODUCT_2))
                    .thenReturn(Optional.empty());

            OrderCreatedEvent event = buildOrderCreatedEvent(List.of(
                    buildItem(PRODUCT_1, 1),
                    buildItem(PRODUCT_2, 1)
            ));

            inventoryService.reserveInventory(event);

            // All-or-nothing: nothing reserved
            verify(inventoryRepository, never()).save(any(Inventory.class));
            verify(reservationRecordRepository, never()).save(any());
            ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxEventRepository).save(captor.capture());
            assertThat(captor.getValue().getTopic()).isEqualTo("inventory.failed");
        }

        @Test
        @DisplayName("outbox event payload contains payment passthrough fields (customerId, totalAmount, paymentMethod)")
        void outboxPayload_containsPaymentPassthrough() {
            Inventory inv = buildInventory(PRODUCT_1, 100, 0);
            when(inventoryRepository.findByProductIdWithProduct(PRODUCT_1))
                    .thenReturn(Optional.of(inv));

            OrderCreatedEvent event = buildOrderCreatedEvent(List.of(
                    buildItem(PRODUCT_1, 1)
            ));

            inventoryService.reserveInventory(event);

            ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxEventRepository).save(captor.capture());
            String payload = captor.getValue().getPayload();
            assertThat(payload).contains("customer-001");
            assertThat(payload).contains("WALLET");
            assertThat(payload).contains("4097");
        }
    }

    // ── releaseInventory ──────────────────────────────────────────────────

    @Nested
    @DisplayName("releaseInventory")
    class ReleaseInventory {

        @Test
        @DisplayName("releases active reservations: restores stock, marks records RELEASED")
        void happyPath_releasesReservations() {
            ReservationRecord record = ReservationRecord.builder()
                    .orderId(ORDER_ID)
                    .productId(PRODUCT_1)
                    .quantity(2)
                    .status(ReservationStatus.ACTIVE)
                    .build();
            Inventory inv = buildInventory(PRODUCT_1, 98, 2);

            when(reservationRecordRepository.findByOrderIdAndStatus(ORDER_ID, ReservationStatus.ACTIVE))
                    .thenReturn(List.of(record));
            when(inventoryRepository.findByProductIdWithProduct(PRODUCT_1))
                    .thenReturn(Optional.of(inv));

            OrderCancelledEvent event = OrderCancelledEvent.builder()
                    .orderId(ORDER_ID)
                    .customerId("customer-001")
                    .build();

            inventoryService.releaseInventory(event);

            assertThat(inv.getAvailableQuantity()).isEqualTo(100);
            assertThat(inv.getReservedQuantity()).isEqualTo(0);
            verify(inventoryRepository).save(inv);
            verify(reservationRecordRepository).save(record);
            assertThat(record.getStatus()).isEqualTo(ReservationStatus.RELEASED);
        }

        @Test
        @DisplayName("no active reservations: does nothing (no-op)")
        void noActiveReservations_doesNothing() {
            when(reservationRecordRepository.findByOrderIdAndStatus(ORDER_ID, ReservationStatus.ACTIVE))
                    .thenReturn(List.of());

            OrderCancelledEvent event = OrderCancelledEvent.builder()
                    .orderId(ORDER_ID)
                    .customerId("customer-001")
                    .build();

            inventoryService.releaseInventory(event);

            verify(inventoryRepository, never()).save(any());
            verify(reservationRecordRepository, never()).save(any());
        }

        @Test
        @DisplayName("inventory not found for a product: skips that product, continues others")
        void inventoryMissing_skipsAndContinues() {
            ReservationRecord record1 = ReservationRecord.builder()
                    .orderId(ORDER_ID).productId(PRODUCT_1).quantity(2).status(ReservationStatus.ACTIVE).build();
            ReservationRecord record2 = ReservationRecord.builder()
                    .orderId(ORDER_ID).productId(PRODUCT_2).quantity(1).status(ReservationStatus.ACTIVE).build();
            Inventory inv2 = buildInventory(PRODUCT_2, 49, 1);

            when(reservationRecordRepository.findByOrderIdAndStatus(ORDER_ID, ReservationStatus.ACTIVE))
                    .thenReturn(List.of(record1, record2));
            when(inventoryRepository.findByProductIdWithProduct(PRODUCT_1))
                    .thenReturn(Optional.empty()); // missing
            when(inventoryRepository.findByProductIdWithProduct(PRODUCT_2))
                    .thenReturn(Optional.of(inv2));

            OrderCancelledEvent event = OrderCancelledEvent.builder()
                    .orderId(ORDER_ID)
                    .customerId("customer-001")
                    .build();

            inventoryService.releaseInventory(event);

            // Only product2 released
            verify(inventoryRepository).save(inv2);
            assertThat(inv2.getAvailableQuantity()).isEqualTo(50);
            // record1 was NOT released (inventory missing) — must remain ACTIVE
            assertThat(record1.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
            // record2 was released
            verify(reservationRecordRepository, times(1)).save(record2);
            assertThat(record2.getStatus()).isEqualTo(ReservationStatus.RELEASED);
        }
    }

    // ── releaseInventoryByOrderId ──────────────────────────────────────────

    @Nested
    @DisplayName("releaseInventoryByOrderId")
    class ReleaseInventoryByOrderId {

        @Test
        @DisplayName("releases active reservations for failed order")
        void releasesForFailedOrder() {
            ReservationRecord record = ReservationRecord.builder()
                    .orderId(ORDER_ID).productId(PRODUCT_1).quantity(3).status(ReservationStatus.ACTIVE).build();
            Inventory inv = buildInventory(PRODUCT_1, 97, 3);

            when(reservationRecordRepository.findByOrderIdAndStatus(ORDER_ID, ReservationStatus.ACTIVE))
                    .thenReturn(List.of(record));
            when(inventoryRepository.findByProductIdWithProduct(PRODUCT_1))
                    .thenReturn(Optional.of(inv));

            inventoryService.releaseInventoryByOrderId(ORDER_ID);

            assertThat(inv.getAvailableQuantity()).isEqualTo(100);
            verify(inventoryRepository).save(inv);
            verify(reservationRecordRepository).save(record);
        }

        @Test
        @DisplayName("no active reservations: no-op")
        void noReservations_doesNothing() {
            when(reservationRecordRepository.findByOrderIdAndStatus(ORDER_ID, ReservationStatus.ACTIVE))
                    .thenReturn(List.of());

            inventoryService.releaseInventoryByOrderId(ORDER_ID);

            verify(inventoryRepository, never()).save(any());
        }
    }

    // ── getInventory ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("getInventory")
    class GetInventory {

        @Test
        @DisplayName("returns mapped DTO when inventory found")
        void found_returnsMappedDto() {
            Inventory inv = buildInventory(PRODUCT_1, 100, 0);
            InventoryDto dto = InventoryDto.builder().productId(PRODUCT_1.toString()).availableQuantity(100).build();
            when(inventoryRepository.findByProductIdWithProduct(PRODUCT_1)).thenReturn(Optional.of(inv));
            when(inventoryMapper.toDto(inv)).thenReturn(dto);

            InventoryDto result = inventoryService.getInventory(PRODUCT_1);

            assertThat(result).isEqualTo(dto);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when inventory not found")
        void notFound_throwsResourceNotFoundException() {
            when(inventoryRepository.findByProductIdWithProduct(PRODUCT_1)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> inventoryService.getInventory(PRODUCT_1))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── getAllInventory ────────────────────────────────────────────────────

    @Nested
    @DisplayName("getAllInventory")
    class GetAllInventory {

        @Test
        @DisplayName("returns mapped list of all inventory")
        void returnsAllMapped() {
            Inventory inv = buildInventory(PRODUCT_1, 100, 0);
            InventoryDto dto = InventoryDto.builder().productId(PRODUCT_1.toString()).build();
            when(inventoryRepository.findAllWithProduct()).thenReturn(List.of(inv));
            when(inventoryMapper.toDto(inv)).thenReturn(dto);

            List<InventoryDto> result = inventoryService.getAllInventory();

            assertThat(result).hasSize(1).containsExactly(dto);
        }
    }

    // ── restockInventory ──────────────────────────────────────────────────

    @Nested
    @DisplayName("restockInventory")
    class RestockInventory {

        @Test
        @DisplayName("adds stock and returns updated DTO")
        void happyPath_addsStock() {
            Inventory inv = buildInventory(PRODUCT_1, 10, 0);
            InventoryDto dto = InventoryDto.builder().productId(PRODUCT_1.toString()).availableQuantity(60).build();
            when(inventoryRepository.findByProductIdWithProduct(PRODUCT_1)).thenReturn(Optional.of(inv));
            when(inventoryRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(inventoryMapper.toDto(any())).thenReturn(dto);

            RestockRequest request = RestockRequest.builder().quantity(50).reason("Replenishment").build();
            InventoryDto result = inventoryService.restockInventory(PRODUCT_1, request);

            assertThat(inv.getAvailableQuantity()).isEqualTo(60);
            assertThat(result).isEqualTo(dto);
            verify(inventoryRepository).save(inv);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when product not found")
        void notFound_throws() {
            when(inventoryRepository.findByProductIdWithProduct(PRODUCT_1)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> inventoryService.restockInventory(PRODUCT_1,
                    RestockRequest.builder().quantity(10).reason("Test").build()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── getLowStockInventory ──────────────────────────────────────────────

    @Nested
    @DisplayName("getLowStockInventory")
    class GetLowStockInventory {

        @Test
        @DisplayName("returns inventory below threshold")
        void returnsBelowThreshold() {
            Inventory inv = buildInventory(PRODUCT_1, 5, 0);
            InventoryDto dto = InventoryDto.builder().productId(PRODUCT_1.toString()).availableQuantity(5).build();
            when(inventoryRepository.findLowStock(10)).thenReturn(List.of(inv));
            when(inventoryMapper.toDto(inv)).thenReturn(dto);

            List<InventoryDto> result = inventoryService.getLowStockInventory(10);

            assertThat(result).hasSize(1).containsExactly(dto);
        }
    }

    // ── serialization error ───────────────────────────────────────────────

    @Nested
    @DisplayName("outbox serialization")
    class OutboxSerialization {

        @Test
        @DisplayName("throws RuntimeException when ObjectMapper fails to serialize outbox event")
        void serializationFailure_throwsRuntimeException() throws JsonProcessingException {
            ObjectMapper spyMapper = spy(objectMapper);
            doThrow(new JsonProcessingException("fail") {}).when(spyMapper).writeValueAsString(any());
            InventoryService serviceWithSpy = new InventoryService(
                    inventoryRepository, reservationRecordRepository,
                    outboxEventRepository, inventoryMapper, spyMapper);

            // Trigger a failed reservation → publishInventoryFailed → serialization error
            when(inventoryRepository.findByProductIdWithProduct(PRODUCT_1))
                    .thenReturn(Optional.empty());

            OrderCreatedEvent event = buildOrderCreatedEvent(List.of(buildItem(PRODUCT_1, 1)));

            assertThatThrownBy(() -> serviceWithSpy.reserveInventory(event))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to serialize");
        }
    }
}
