package com.paymentplatform.inventoryservice.infrastructure.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentplatform.commonlib.constants.KafkaTopics;
import com.paymentplatform.commonlib.enums.InventoryStatus;
import com.paymentplatform.inventoryservice.domain.entity.Inventory;
import com.paymentplatform.inventoryservice.domain.entity.OutboxEvent;
import com.paymentplatform.inventoryservice.domain.entity.OutboxStatus;
import com.paymentplatform.inventoryservice.domain.entity.Product;
import com.paymentplatform.inventoryservice.domain.entity.ReservationRecord;
import com.paymentplatform.inventoryservice.domain.entity.ReservationStatus;
import com.paymentplatform.inventoryservice.domain.repository.InventoryRepository;
import com.paymentplatform.inventoryservice.domain.repository.OutboxEventRepository;
import com.paymentplatform.inventoryservice.domain.repository.ReservationRecordRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReservationExpiryProcessor Tests")
class ReservationExpiryProcessorTest {

    @Mock
    private ReservationRecordRepository reservationRecordRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    @DisplayName("processExpiredReservations returns early when nothing expired")
    void processExpiredReservations_noExpiredReservations() {
        ReservationExpiryProcessor processor = new ReservationExpiryProcessor(
                reservationRecordRepository, inventoryRepository, outboxEventRepository, objectMapper
        );
        ReflectionTestUtils.setField(processor, "ttlMinutes", 15);
        when(reservationRecordRepository.findExpiredReservations(org.mockito.ArgumentMatchers.eq(ReservationStatus.ACTIVE), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());

        processor.processExpiredReservations();

        verify(reservationRecordRepository).findExpiredReservations(org.mockito.ArgumentMatchers.eq(ReservationStatus.ACTIVE), org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(inventoryRepository, outboxEventRepository);
    }

    @Test
    @DisplayName("processExpiredReservations releases stock, expires records, and saves one outbox event per order")
    void processExpiredReservations_releasesAndPublishesFailureEvent() {
        ReservationExpiryProcessor processor = new ReservationExpiryProcessor(
                reservationRecordRepository, inventoryRepository, outboxEventRepository, objectMapper
        );
        ReflectionTestUtils.setField(processor, "ttlMinutes", 15);

        UUID productId1 = UUID.randomUUID();
        UUID productId2 = UUID.randomUUID();
        ReservationRecord record1 = ReservationRecord.builder()
                .id(UUID.randomUUID())
                .orderId("order-1")
                .productId(productId1)
                .quantity(2)
                .status(ReservationStatus.ACTIVE)
                .reservedAt(Instant.now().minusSeconds(3600))
                .build();
        ReservationRecord record2 = ReservationRecord.builder()
                .id(UUID.randomUUID())
                .orderId("order-1")
                .productId(productId2)
                .quantity(1)
                .status(ReservationStatus.ACTIVE)
                .reservedAt(Instant.now().minusSeconds(3600))
                .build();
        Inventory inventory1 = Inventory.builder()
                .id(UUID.randomUUID())
                .product(Product.builder().id(productId1).sku("SKU-1").name("Item 1").build())
                .availableQuantity(5)
                .reservedQuantity(3)
                .warehouseId("WH-001")
                .status(InventoryStatus.AVAILABLE)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        Inventory inventory2 = Inventory.builder()
                .id(UUID.randomUUID())
                .product(Product.builder().id(productId2).sku("SKU-2").name("Item 2").build())
                .availableQuantity(0)
                .reservedQuantity(1)
                .warehouseId("WH-001")
                .status(InventoryStatus.DEPLETED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(reservationRecordRepository.findExpiredReservations(org.mockito.ArgumentMatchers.eq(ReservationStatus.ACTIVE), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(record1, record2));
        when(inventoryRepository.findByProductIdWithProduct(productId1)).thenReturn(Optional.of(inventory1));
        when(inventoryRepository.findByProductIdWithProduct(productId2)).thenReturn(Optional.of(inventory2));

        processor.processExpiredReservations();

        assertThat(record1.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(record2.getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(inventory1.getAvailableQuantity()).isEqualTo(7);
        assertThat(inventory1.getReservedQuantity()).isEqualTo(1);
        assertThat(inventory2.getAvailableQuantity()).isEqualTo(1);
        assertThat(inventory2.getReservedQuantity()).isZero();

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        OutboxEvent outbox = outboxCaptor.getValue();
        assertThat(outbox.getAggregateId()).isEqualTo("order-1");
        assertThat(outbox.getTopic()).isEqualTo(KafkaTopics.INVENTORY_FAILED);
        assertThat(outbox.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(outbox.getPayload()).contains("Reservation expired after 15 minutes");
    }
}
