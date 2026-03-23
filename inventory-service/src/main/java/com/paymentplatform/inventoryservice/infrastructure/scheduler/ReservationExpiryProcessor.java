package com.paymentplatform.inventoryservice.infrastructure.scheduler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentplatform.commonlib.constants.KafkaTopics;
import com.paymentplatform.commonlib.events.InventoryFailedEvent;
import com.paymentplatform.inventoryservice.domain.entity.Inventory;
import com.paymentplatform.inventoryservice.domain.entity.OutboxEvent;
import com.paymentplatform.inventoryservice.domain.entity.OutboxStatus;
import com.paymentplatform.inventoryservice.domain.entity.ReservationRecord;
import com.paymentplatform.inventoryservice.domain.entity.ReservationStatus;
import com.paymentplatform.inventoryservice.domain.repository.InventoryRepository;
import com.paymentplatform.inventoryservice.domain.repository.OutboxEventRepository;
import com.paymentplatform.inventoryservice.domain.repository.ReservationRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Periodically scans for ACTIVE reservations that have exceeded their TTL.
 * Releases inventory back to available stock and publishes inventory.failed
 * so the order-service can cancel the stuck order.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReservationExpiryProcessor {

    private final ReservationRecordRepository reservationRecordRepository;
    private final InventoryRepository inventoryRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Value("${inventory.reservation.ttl-minutes:15}")
    private int ttlMinutes;

    @Scheduled(fixedDelayString = "${inventory.reservation.expiry-check-interval-ms:60000}")
    @Transactional
    public void processExpiredReservations() {
        Instant cutoff = Instant.now().minus(ttlMinutes, ChronoUnit.MINUTES);

        List<ReservationRecord> expired =
                reservationRecordRepository.findExpiredReservations(ReservationStatus.ACTIVE, cutoff);

        if (expired.isEmpty()) {
            return;
        }

        log.info("Found {} expired reservations (cutoff={})", expired.size(), cutoff);

        // Group by orderId so we publish one event per order
        Map<String, List<ReservationRecord>> byOrder = new LinkedHashMap<>();
        for (ReservationRecord record : expired) {
            byOrder.computeIfAbsent(record.getOrderId(), k -> new ArrayList<>()).add(record);
        }

        for (Map.Entry<String, List<ReservationRecord>> entry : byOrder.entrySet()) {
            String orderId = entry.getKey();
            List<ReservationRecord> records = entry.getValue();

            List<InventoryFailedEvent.FailedItem> failedItems = new ArrayList<>();

            for (ReservationRecord record : records) {
                Inventory inventory = inventoryRepository.findByProductIdWithProduct(record.getProductId())
                        .orElse(null);

                if (inventory != null) {
                    inventory.release(record.getQuantity());
                    inventoryRepository.save(inventory);
                    log.debug("Expired: released {} units of product {} for order {}",
                            record.getQuantity(), record.getProductId(), orderId);
                }

                record.expire();
                reservationRecordRepository.save(record);

                failedItems.add(InventoryFailedEvent.FailedItem.builder()
                        .productId(record.getProductId().toString())
                        .requestedQuantity(record.getQuantity())
                        .availableQuantity(inventory != null ? inventory.getAvailableQuantity() : 0)
                        .build());
            }

            // Publish inventory.failed so order-service cancels the stuck order
            InventoryFailedEvent event = InventoryFailedEvent.builder()
                    .orderId(orderId)
                    .failedItems(failedItems)
                    .failureReason("Reservation expired after " + ttlMinutes + " minutes")
                    .failedAt(Instant.now())
                    .build();

            saveOutboxEvent(orderId, event);
            log.info("Expired reservations for order {}: {} items released, inventory.failed published",
                    orderId, records.size());
        }
    }

    private void saveOutboxEvent(String orderId, InventoryFailedEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            OutboxEvent outbox = OutboxEvent.builder()
                    .aggregateType("Inventory")
                    .aggregateId(orderId)
                    .eventType(KafkaTopics.INVENTORY_FAILED)
                    .topic(KafkaTopics.INVENTORY_FAILED)
                    .payload(payload)
                    .status(OutboxStatus.PENDING)
                    .build();
            outboxEventRepository.save(outbox);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize expiry event for outbox", e);
        }
    }
}
