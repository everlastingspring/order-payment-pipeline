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

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private final OrderSagaManager sagaManager;
    private final ObjectMapper objectMapper;

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
