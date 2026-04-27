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
 * Kafka consumer that triggers payment processing in the choreography saga.
 *
 * <p><strong>Sequential saga trigger:</strong> payment-service consumes {@code inventory.reserved},
 * NOT {@code order.created}. This design guarantees that stock is locked before payment is ever
 * attempted — preventing the scenario where a customer is charged for an out-of-stock item.</p>
 *
 * <p><strong>Saga flow:</strong></p>
 * <pre>
 *   order.created (order-service)
 *       → inventory.reserved (inventory-service)
 *           → [THIS] payment.completed / payment.failed (payment-service)
 * </pre>
 *
 * <p>On failure (exception thrown), the message is not ack'd and Kafka redelivers it.
 * {@code PaymentService.processPayment()} handles idempotency on redelivery via the
 * {@code idempotency_keys} table.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    /** Core payment service that handles the saga logic, idempotency, and outbox. */
    private final PaymentService paymentService;

    /** Deserialises the raw Kafka JSON payload into typed event objects. */
    private final ObjectMapper objectMapper;

    /**
     * Handles {@code inventory.reserved} — the trigger for the payment step of the saga.
     * Delegates to {@code PaymentService.processPayment()} which acquires a Redis lock,
     * checks idempotency, charges the wallet, and writes outbox events.
     *
     * @param payload raw JSON string from Kafka
     * @param ack     manual acknowledgment handle; ack'd only on success
     */
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
