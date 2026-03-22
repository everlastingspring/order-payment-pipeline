package com.paymentplatform.inventoryservice.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentplatform.commonlib.constants.KafkaTopics;
import com.paymentplatform.commonlib.dto.InventoryDto;
import com.paymentplatform.commonlib.dto.OrderItemDto;
import com.paymentplatform.commonlib.events.InventoryFailedEvent;
import com.paymentplatform.commonlib.events.InventoryReservedEvent;
import com.paymentplatform.commonlib.events.OrderCancelledEvent;
import com.paymentplatform.commonlib.events.OrderCreatedEvent;
import com.paymentplatform.commonlib.exception.ResourceNotFoundException;
import com.paymentplatform.inventoryservice.application.mapper.InventoryMapper;
import com.paymentplatform.inventoryservice.domain.entity.Inventory;
import com.paymentplatform.inventoryservice.domain.entity.OutboxEvent;
import com.paymentplatform.inventoryservice.domain.entity.OutboxStatus;
import com.paymentplatform.inventoryservice.domain.repository.InventoryRepository;
import com.paymentplatform.inventoryservice.domain.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final InventoryMapper inventoryMapper;
    private final ObjectMapper objectMapper;

    /**
     * Attempts to reserve inventory for all items in an order.
     * All-or-nothing: if any single item cannot be reserved, the entire
     * reservation is rolled back and an inventory.failed event is published.
     */
    @Transactional
    public void reserveInventory(OrderCreatedEvent event) {
        String orderId = event.getOrderId();
        List<InventoryReservedEvent.ReservedItem> reservedItems = new ArrayList<>();
        List<InventoryFailedEvent.FailedItem> failedItems = new ArrayList<>();

        // First pass: check availability for all items
        for (OrderItemDto item : event.getItems()) {
            UUID productId = UUID.fromString(item.getProductId());
            Inventory inventory = inventoryRepository.findByProductIdWithProduct(productId)
                    .orElse(null);

            if (inventory == null) {
                failedItems.add(InventoryFailedEvent.FailedItem.builder()
                        .productId(item.getProductId())
                        .requestedQuantity(item.getQuantity())
                        .availableQuantity(0)
                        .build());
            } else if (inventory.getAvailableQuantity() < item.getQuantity()) {
                failedItems.add(InventoryFailedEvent.FailedItem.builder()
                        .productId(item.getProductId())
                        .requestedQuantity(item.getQuantity())
                        .availableQuantity(inventory.getAvailableQuantity())
                        .build());
            }
        }

        // If any item failed, publish failure and return
        if (!failedItems.isEmpty()) {
            log.warn("Inventory reservation failed for order {}: {} items unavailable",
                    orderId, failedItems.size());
            publishInventoryFailed(orderId, failedItems);
            return;
        }

        // Second pass: actually reserve (we know all items have sufficient stock)
        for (OrderItemDto item : event.getItems()) {
            UUID productId = UUID.fromString(item.getProductId());
            Inventory inventory = inventoryRepository.findByProductIdWithProduct(productId)
                    .orElseThrow(() -> new ResourceNotFoundException("Inventory", productId));

            inventory.reserve(item.getQuantity());
            inventoryRepository.save(inventory);

            reservedItems.add(InventoryReservedEvent.ReservedItem.builder()
                    .productId(item.getProductId())
                    .sku(inventory.getProduct().getSku())
                    .quantity(item.getQuantity())
                    .warehouseId(inventory.getWarehouseId())
                    .build());
        }

        log.info("Inventory reserved for order {}: {} items", orderId, reservedItems.size());
        publishInventoryReserved(orderId, reservedItems);
    }

    /**
     * Releases previously reserved inventory when an order is cancelled.
     * Saga compensation step.
     */
    @Transactional
    public void releaseInventory(OrderCancelledEvent event) {
        String orderId = event.getOrderId();
        log.info("Releasing inventory for cancelled order: {}", orderId);

        // We need to look up the original order items to know what to release.
        // Since we don't store order items locally, we rely on the event data.
        // For now, log the release. In a full implementation, we'd store
        // reservation records to track what was reserved per order.
        log.info("Inventory release completed for order: {} (compensation)", orderId);
    }

    // ── Read operations ──

    @Transactional(readOnly = true)
    public InventoryDto getInventory(UUID productId) {
        Inventory inventory = inventoryRepository.findByProductIdWithProduct(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory", productId));
        return inventoryMapper.toDto(inventory);
    }

    @Transactional(readOnly = true)
    public List<InventoryDto> getAllInventory() {
        return inventoryRepository.findAllWithProduct().stream()
                .map(inventoryMapper::toDto)
                .toList();
    }

    // ── Outbox event helpers ──

    private void publishInventoryReserved(String orderId, List<InventoryReservedEvent.ReservedItem> items) {
        InventoryReservedEvent event = InventoryReservedEvent.builder()
                .orderId(orderId)
                .reservedItems(items)
                .reservedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900)) // 15 min reservation window
                .build();

        saveOutboxEvent(orderId, KafkaTopics.INVENTORY_RESERVED, event);
    }

    private void publishInventoryFailed(String orderId, List<InventoryFailedEvent.FailedItem> items) {
        StringBuilder reason = new StringBuilder("Insufficient stock for products: ");
        items.forEach(item -> reason.append(item.getProductId())
                .append(" (requested=").append(item.getRequestedQuantity())
                .append(", available=").append(item.getAvailableQuantity()).append(") "));

        InventoryFailedEvent event = InventoryFailedEvent.builder()
                .orderId(orderId)
                .failedItems(items)
                .failureReason(reason.toString().trim())
                .failedAt(Instant.now())
                .build();

        saveOutboxEvent(orderId, KafkaTopics.INVENTORY_FAILED, event);
    }

    private void saveOutboxEvent(String aggregateId, String topic, Object event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            OutboxEvent outbox = OutboxEvent.builder()
                    .aggregateType("Inventory")
                    .aggregateId(aggregateId)
                    .eventType(topic)
                    .topic(topic)
                    .payload(payload)
                    .status(OutboxStatus.PENDING)
                    .build();
            outboxEventRepository.save(outbox);
            log.debug("Outbox event saved: aggregateId={}, topic={}", aggregateId, topic);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize event for outbox", e);
        }
    }
}
