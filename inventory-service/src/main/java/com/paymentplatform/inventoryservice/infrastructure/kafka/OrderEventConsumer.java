package com.paymentplatform.inventoryservice.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentplatform.commonlib.constants.KafkaTopics;
import com.paymentplatform.commonlib.events.OrderCancelledEvent;
import com.paymentplatform.commonlib.events.OrderCreatedEvent;
import com.paymentplatform.commonlib.events.OrderFailedEvent;
import com.paymentplatform.inventoryservice.application.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for order lifecycle events that affect inventory.
 *
 * <p><strong>Topics consumed:</strong></p>
 * <ul>
 *   <li>{@code order.created} → {@link InventoryService#reserveInventory(OrderCreatedEvent)} —
 *       lock stock for all items; publishes {@code inventory.reserved} or {@code inventory.failed}.</li>
 *   <li>{@code order.cancelled} → {@link InventoryService#releaseInventory(OrderCancelledEvent)} —
 *       saga compensation: return reserved units to available stock (user-initiated cancel).</li>
 *   <li>{@code order.failed} — partial compensation: only releases if {@code failedStep=PAYMENT}.
 *       If the inventory step itself failed, stock was never reserved, so nothing to release.</li>
 * </ul>
 *
 * <p>Manual acknowledgment is used ({@code AckMode.MANUAL_IMMEDIATE}) so that a successfully
 * processed event is committed immediately and a failed event throws to trigger Resilience4j retry.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    /** Handles all inventory business logic — reservation, release, compensation. */
    private final InventoryService inventoryService;

    /** Deserialises the raw Kafka JSON payload into typed event objects. */
    private final ObjectMapper objectMapper;

    /**
     * Handles {@code order.created} — triggers the inventory reservation step of the saga.
     * Delegates to {@code InventoryService.reserveInventory()} which performs a two-pass
     * check-then-commit and writes either {@code inventory.reserved} or {@code inventory.failed}
     * to the outbox.
     *
     * @param payload raw JSON string from Kafka
     * @param ack     manual acknowledgment handle; ack'd only on success
     */
    @KafkaListener(
            topics = KafkaTopics.ORDER_CREATED,
            groupId = "inventory-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleOrderCreated(String payload, Acknowledgment ack) {
        try {
            OrderCreatedEvent event = objectMapper.readValue(payload, OrderCreatedEvent.class);
            log.info("Received order.created: orderId={}, items={}",
                    event.getOrderId(), event.getItems().size());
            inventoryService.reserveInventory(event);
            ack.acknowledge();
            log.info("order.created processed and acknowledged: orderId={}", event.getOrderId());
        } catch (Exception e) {
            log.error("Failed to process order.created: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Handles {@code order.cancelled} — returns all ACTIVE reserved units to available stock.
     * This is the standard saga compensation path for user-initiated order cancellation.
     *
     * @param payload raw JSON string from Kafka
     * @param ack     manual acknowledgment handle
     */
    @KafkaListener(
            topics = KafkaTopics.ORDER_CANCELLED,
            groupId = "inventory-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleOrderCancelled(String payload, Acknowledgment ack) {
        try {
            OrderCancelledEvent event = objectMapper.readValue(payload, OrderCancelledEvent.class);
            log.info("Received order.cancelled: orderId={}, reason={}",
                    event.getOrderId(), event.getCancellationReason());
            inventoryService.releaseInventory(event);
            ack.acknowledge();
            log.info("order.cancelled processed and acknowledged: orderId={}", event.getOrderId());
        } catch (Exception e) {
            log.error("Failed to process order.cancelled: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Handles {@code order.failed} — conditionally releases inventory.
     *
     * <p>Release happens only when {@code failedStep=PAYMENT}: stock was reserved but payment
     * was declined, so inventory must be returned. When {@code failedStep=INVENTORY} the stock
     * was never reserved, so there is nothing to release.</p>
     *
     * @param payload raw JSON string from Kafka
     * @param ack     manual acknowledgment handle
     */
    @KafkaListener(
            topics = KafkaTopics.ORDER_FAILED,
            groupId = "inventory-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleOrderFailed(String payload, Acknowledgment ack) {
        try {
            OrderFailedEvent event = objectMapper.readValue(payload, OrderFailedEvent.class);
            log.info("Received order.failed: orderId={}, failedStep={}, reason={}",
                    event.getOrderId(), event.getFailedStep(), event.getFailureReason());
            // Only release if payment step failed — inventory was reserved but payment declined.
            // If inventory step failed, stock was never reserved so nothing to release.
            if ("PAYMENT".equals(event.getFailedStep())) {
                inventoryService.releaseInventoryByOrderId(event.getOrderId());
            }
            ack.acknowledge();
            log.info("order.failed processed and acknowledged: orderId={}", event.getOrderId());
        } catch (Exception e) {
            log.error("Failed to process order.failed: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}
