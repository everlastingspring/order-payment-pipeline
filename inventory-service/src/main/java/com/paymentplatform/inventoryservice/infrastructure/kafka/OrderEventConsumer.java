package com.paymentplatform.inventoryservice.infrastructure.kafka;

import com.paymentplatform.commonlib.constants.KafkaTopics;
import com.paymentplatform.commonlib.events.OrderCancelledEvent;
import com.paymentplatform.commonlib.events.OrderCreatedEvent;
import com.paymentplatform.inventoryservice.application.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Consumes order events relevant to inventory:
 *  - order.created  → reserve stock for each item
 *  - order.cancelled → release previously reserved stock
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final InventoryService inventoryService;

    @KafkaListener(
            topics = KafkaTopics.ORDER_CREATED,
            groupId = "inventory-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleOrderCreated(OrderCreatedEvent event, Acknowledgment ack) {
        log.info("Received order.created: orderId={}, items={}",
                event.getOrderId(), event.getItems().size());

        try {
            inventoryService.reserveInventory(event);
            ack.acknowledge();
            log.info("order.created processed and acknowledged: orderId={}", event.getOrderId());
        } catch (Exception e) {
            log.error("Failed to process order.created: orderId={}, error={}",
                    event.getOrderId(), e.getMessage(), e);
            throw e;
        }
    }

    @KafkaListener(
            topics = KafkaTopics.ORDER_CANCELLED,
            groupId = "inventory-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleOrderCancelled(OrderCancelledEvent event, Acknowledgment ack) {
        log.info("Received order.cancelled: orderId={}, reason={}",
                event.getOrderId(), event.getCancellationReason());

        try {
            inventoryService.releaseInventory(event);
            ack.acknowledge();
            log.info("order.cancelled processed and acknowledged: orderId={}", event.getOrderId());
        } catch (Exception e) {
            log.error("Failed to process order.cancelled: orderId={}, error={}",
                    event.getOrderId(), e.getMessage(), e);
            throw e;
        }
    }
}
