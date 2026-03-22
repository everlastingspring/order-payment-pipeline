package com.paymentplatform.orderservice.infrastructure.kafka;

import com.paymentplatform.commonlib.constants.KafkaTopics;
import com.paymentplatform.commonlib.events.InventoryFailedEvent;
import com.paymentplatform.commonlib.events.InventoryReservedEvent;
import com.paymentplatform.orderservice.application.saga.OrderSagaManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryEventConsumer {

    private final OrderSagaManager sagaManager;

    @KafkaListener(
            topics = KafkaTopics.INVENTORY_RESERVED,
            groupId = "order-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onInventoryReserved(InventoryReservedEvent event, Acknowledgment ack) {
        log.info("Received inventory.reserved: orderId={}, reservationId={}",
                event.getOrderId(), event.getReservationId());
        try {
            sagaManager.handleInventoryReserved(event);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error processing inventory.reserved for order {}: {}",
                    event.getOrderId(), e.getMessage(), e);
            throw e;
        }
    }

    @KafkaListener(
            topics = KafkaTopics.INVENTORY_FAILED,
            groupId = "order-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onInventoryFailed(InventoryFailedEvent event, Acknowledgment ack) {
        log.info("Received inventory.failed: orderId={}, reason={}",
                event.getOrderId(), event.getFailureReason());
        try {
            sagaManager.handleInventoryFailed(event);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error processing inventory.failed for order {}: {}",
                    event.getOrderId(), e.getMessage(), e);
            throw e;
        }
    }
}
