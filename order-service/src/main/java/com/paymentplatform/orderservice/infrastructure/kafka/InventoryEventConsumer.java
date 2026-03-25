package com.paymentplatform.orderservice.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = KafkaTopics.INVENTORY_RESERVED,
            groupId = "order-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onInventoryReserved(String payload, Acknowledgment ack) {
        try {
            InventoryReservedEvent event = objectMapper.readValue(payload, InventoryReservedEvent.class);
            log.info("Received inventory.reserved: orderId={}, reservationId={}",
                    event.getOrderId(), event.getReservationId());
            sagaManager.handleInventoryReserved(event);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error processing inventory.reserved: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @KafkaListener(
            topics = KafkaTopics.INVENTORY_FAILED,
            groupId = "order-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onInventoryFailed(String payload, Acknowledgment ack) {
        try {
            InventoryFailedEvent event = objectMapper.readValue(payload, InventoryFailedEvent.class);
            log.info("Received inventory.failed: orderId={}, reason={}",
                    event.getOrderId(), event.getFailureReason());
            sagaManager.handleInventoryFailed(event);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error processing inventory.failed: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}
