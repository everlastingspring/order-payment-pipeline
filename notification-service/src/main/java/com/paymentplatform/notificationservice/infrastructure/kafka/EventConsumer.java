package com.paymentplatform.notificationservice.infrastructure.kafka;

import com.paymentplatform.commonlib.constants.KafkaTopics;
import com.paymentplatform.commonlib.enums.NotificationType;
import com.paymentplatform.commonlib.events.*;
import com.paymentplatform.notificationservice.application.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Consumes all order/payment lifecycle events and delegates
 * to NotificationService to create and send notifications.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventConsumer {

    private final NotificationService notificationService;

    @KafkaListener(
            topics = KafkaTopics.ORDER_CREATED,
            groupId = "notification-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleOrderCreated(OrderCreatedEvent event, Acknowledgment ack) {
        log.info("Received order.created: orderId={}", event.getOrderId());
        try {
            notificationService.sendNotification(
                    event.getCustomerId(),
                    event.getCustomerEmail(),
                    NotificationType.ORDER_CONFIRMATION,
                    "Order Received — #" + event.getOrderId().substring(0, 8),
                    buildOrderCreatedBody(event),
                    event.getOrderId(),
                    KafkaTopics.ORDER_CREATED,
                    event.getEventId() != null ? event.getEventId().toString() : null
            );
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process order.created notification: {}", e.getMessage(), e);
            throw e;
        }
    }

    @KafkaListener(
            topics = KafkaTopics.PAYMENT_COMPLETED,
            groupId = "notification-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handlePaymentCompleted(PaymentCompletedEvent event, Acknowledgment ack) {
        log.info("Received payment.completed: orderId={}, paymentId={}", event.getOrderId(), event.getPaymentId());
        try {
            notificationService.sendNotification(
                    event.getCustomerId(),
                    null, // will be resolved from order context if needed
                    NotificationType.PAYMENT_SUCCESS,
                    "Payment Successful — Order #" + event.getOrderId().substring(0, 8),
                    buildPaymentCompletedBody(event),
                    event.getOrderId(),
                    KafkaTopics.PAYMENT_COMPLETED,
                    event.getEventId() != null ? event.getEventId().toString() : null
            );
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process payment.completed notification: {}", e.getMessage(), e);
            throw e;
        }
    }

    @KafkaListener(
            topics = KafkaTopics.PAYMENT_FAILED,
            groupId = "notification-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handlePaymentFailed(PaymentFailedEvent event, Acknowledgment ack) {
        log.info("Received payment.failed: orderId={}", event.getOrderId());
        try {
            notificationService.sendNotification(
                    event.getCustomerId(),
                    null,
                    NotificationType.PAYMENT_FAILURE,
                    "Payment Failed — Order #" + event.getOrderId().substring(0, 8),
                    buildPaymentFailedBody(event),
                    event.getOrderId(),
                    KafkaTopics.PAYMENT_FAILED,
                    event.getEventId() != null ? event.getEventId().toString() : null
            );
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process payment.failed notification: {}", e.getMessage(), e);
            throw e;
        }
    }

    @KafkaListener(
            topics = KafkaTopics.ORDER_COMPLETED,
            groupId = "notification-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleOrderCompleted(OrderCompletedEvent event, Acknowledgment ack) {
        log.info("Received order.completed: orderId={}", event.getOrderId());
        try {
            notificationService.sendNotification(
                    event.getCustomerId(),
                    event.getCustomerEmail(),
                    NotificationType.ORDER_CONFIRMATION,
                    "Order Confirmed — #" + event.getOrderId().substring(0, 8),
                    buildOrderCompletedBody(event),
                    event.getOrderId(),
                    KafkaTopics.ORDER_COMPLETED,
                    event.getEventId() != null ? event.getEventId().toString() : null
            );
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process order.completed notification: {}", e.getMessage(), e);
            throw e;
        }
    }

    @KafkaListener(
            topics = KafkaTopics.ORDER_CANCELLED,
            groupId = "notification-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleOrderCancelled(OrderCancelledEvent event, Acknowledgment ack) {
        log.info("Received order.cancelled: orderId={}", event.getOrderId());
        try {
            notificationService.sendNotification(
                    event.getCustomerId(),
                    event.getCustomerEmail(),
                    NotificationType.ORDER_CANCELLED,
                    "Order Cancelled — #" + event.getOrderId().substring(0, 8),
                    buildOrderCancelledBody(event),
                    event.getOrderId(),
                    KafkaTopics.ORDER_CANCELLED,
                    event.getEventId() != null ? event.getEventId().toString() : null
            );
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process order.cancelled notification: {}", e.getMessage(), e);
            throw e;
        }
    }

    // ── Email body builders ──

    private String buildOrderCreatedBody(OrderCreatedEvent e) {
        return String.format("Your order #%s has been received. Total: %s %s. We're processing it now.",
                e.getOrderId().substring(0, 8), e.getTotalAmount().getAmount(), e.getTotalAmount().getCurrency());
    }

    private String buildPaymentCompletedBody(PaymentCompletedEvent e) {
        return String.format("Payment of %s %s for order #%s was successful. Transaction ref: %s",
                e.getAmount().getAmount(), e.getAmount().getCurrency(),
                e.getOrderId().substring(0, 8), e.getTransactionReference());
    }

    private String buildPaymentFailedBody(PaymentFailedEvent e) {
        return String.format("Payment of %s %s for order #%s failed. Reason: %s. Please try again.",
                e.getAttemptedAmount().getAmount(), e.getAttemptedAmount().getCurrency(),
                e.getOrderId().substring(0, 8), e.getFailureReason());
    }

    private String buildOrderCompletedBody(OrderCompletedEvent e) {
        return String.format("Your order #%s is confirmed! Total: %s %s. Thank you for your purchase.",
                e.getOrderId().substring(0, 8), e.getTotalAmount().getAmount(), e.getTotalAmount().getCurrency());
    }

    private String buildOrderCancelledBody(OrderCancelledEvent e) {
        return String.format("Your order #%s has been cancelled. Reason: %s",
                e.getOrderId().substring(0, 8), e.getCancellationReason());
    }
}
