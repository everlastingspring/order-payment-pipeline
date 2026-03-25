package com.paymentplatform.paymentservice.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentplatform.commonlib.constants.KafkaTopics;
import com.paymentplatform.commonlib.events.InventoryReservedEvent;
import com.paymentplatform.paymentservice.application.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Sequential saga — payment-service is triggered by inventory.reserved, NOT order.created.
 *
 * Flow: order.created → inventory.reserved → [this] payment.completed/payment.failed
 *
 * Payment only runs after inventory is confirmed reserved.
 * This prevents charging a customer when stock is unavailable.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = KafkaTopics.INVENTORY_RESERVED,
            groupId = "payment-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleInventoryReserved(String payload, Acknowledgment ack) {
        try {
            InventoryReservedEvent event = objectMapper.readValue(payload, InventoryReservedEvent.class);
            log.info("Received inventory.reserved: orderId={}, customerId={}, amount={}",
                    event.getOrderId(), event.getCustomerId(), event.getTotalAmount());
            paymentService.processPayment(event);
            ack.acknowledge();
            log.info("inventory.reserved processed and payment initiated: orderId={}", event.getOrderId());
        } catch (Exception e) {
            log.error("Failed to process inventory.reserved: {}", e.getMessage(), e);
            // Don't ack — Kafka will redeliver
            throw new RuntimeException(e);
        }
    }
}
