package com.paymentplatform.orderservice.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentplatform.commonlib.constants.KafkaTopics;
import com.paymentplatform.commonlib.events.PaymentCompletedEvent;
import com.paymentplatform.commonlib.events.PaymentFailedEvent;
import com.paymentplatform.orderservice.application.saga.OrderSagaManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for payment lifecycle events that complete or fail the order saga.
 *
 * <p><strong>Topics consumed:</strong></p>
 * <ul>
 *   <li>{@code payment.completed} → {@link OrderSagaManager#handlePaymentCompleted} —
 *       transitions order to {@code CONFIRMED}; publishes {@code order.completed}.</li>
 *   <li>{@code payment.failed} → {@link OrderSagaManager#handlePaymentFailed} —
 *       transitions order to {@code FAILED}; publishes {@code order.failed} (failedStep=PAYMENT)
 *       so inventory-service releases the reserved stock.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    /** Saga state machine that handles the actual order status transitions. */
    private final OrderSagaManager sagaManager;

    /** Deserialises raw Kafka JSON into typed event objects. */
    private final ObjectMapper objectMapper;

    /**
     * Handles {@code payment.completed} — the final happy-path step of the saga.
     * Order transitions to CONFIRMED and {@code order.completed} is published.
     *
     * @param payload raw JSON string from Kafka
     * @param ack     manual acknowledgment handle
     */
    @KafkaListener(
            topics = KafkaTopics.PAYMENT_COMPLETED,
            groupId = "order-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onPaymentCompleted(String payload, Acknowledgment ack) {
        try {
            PaymentCompletedEvent event = objectMapper.readValue(payload, PaymentCompletedEvent.class);
            log.info("Received payment.completed: orderId={}, paymentId={}",
                    event.getOrderId(), event.getPaymentId());
            sagaManager.handlePaymentCompleted(event);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error processing payment.completed: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Handles {@code payment.failed} — terminates the saga with FAILED status.
     * Inventory WAS reserved at this point; {@code order.failed} (failedStep=PAYMENT) triggers
     * inventory-service to release the locked stock.
     *
     * @param payload raw JSON string from Kafka
     * @param ack     manual acknowledgment handle
     */
    @KafkaListener(
            topics = KafkaTopics.PAYMENT_FAILED,
            groupId = "order-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onPaymentFailed(String payload, Acknowledgment ack) {
        try {
            PaymentFailedEvent event = objectMapper.readValue(payload, PaymentFailedEvent.class);
            log.info("Received payment.failed: orderId={}, reason={}",
                    event.getOrderId(), event.getFailureReason());
            sagaManager.handlePaymentFailed(event);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error processing payment.failed: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}
