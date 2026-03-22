package com.paymentplatform.orderservice.infrastructure.kafka;

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

    @KafkaListener(
            topics = KafkaTopics.PAYMENT_COMPLETED,
            groupId = "order-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onPaymentCompleted(PaymentCompletedEvent event, Acknowledgment ack) {
        log.info("Received payment.completed: orderId={}, paymentId={}",
                event.getOrderId(), event.getPaymentId());
        try {
            sagaManager.handlePaymentCompleted(event);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error processing payment.completed for order {}: {}",
                    event.getOrderId(), e.getMessage(), e);
            throw e;
        }
    }

    @KafkaListener(
            topics = KafkaTopics.PAYMENT_FAILED,
            groupId = "order-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onPaymentFailed(PaymentFailedEvent event, Acknowledgment ack) {
        log.info("Received payment.failed: orderId={}, reason={}",
                event.getOrderId(), event.getFailureReason());
        try {
            sagaManager.handlePaymentFailed(event);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error processing payment.failed for order {}: {}",
                    event.getOrderId(), e.getMessage(), e);
            throw e;
        }
    }
}
