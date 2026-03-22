package com.paymentplatform.paymentservice.infrastructure.kafka;

import com.paymentplatform.commonlib.constants.KafkaTopics;
import com.paymentplatform.commonlib.events.OrderCreatedEvent;
import com.paymentplatform.paymentservice.application.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Consumes order.created events and triggers payment processing.
 * Uses manual acknowledgment — offset is only committed after
 * PaymentService successfully processes the payment (or records
 * the idempotency key for a duplicate).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final PaymentService paymentService;

    @KafkaListener(
            topics = KafkaTopics.ORDER_CREATED,
            groupId = "payment-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleOrderCreated(OrderCreatedEvent event, Acknowledgment ack) {
        log.info("Received order.created: orderId={}, customerId={}, amount={}",
                event.getOrderId(), event.getCustomerId(), event.getTotalAmount());

        try {
            paymentService.processPayment(event);
            ack.acknowledge();
            log.info("order.created processed and acknowledged: orderId={}", event.getOrderId());
        } catch (Exception e) {
            log.error("Failed to process order.created: orderId={}, error={}",
                    event.getOrderId(), e.getMessage(), e);
            // Don't ack — Kafka will redeliver
            throw e;
        }
    }
}
