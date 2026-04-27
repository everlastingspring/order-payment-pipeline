package com.paymentplatform.notificationservice.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Kafka consumer for all order and payment lifecycle events that produce customer notifications.
 *
 * <p><strong>Topics consumed:</strong></p>
 * <table border="1">
 *   <tr><th>Topic</th><th>Notification type</th></tr>
 *   <tr><td>{@code order.created}</td><td>ORDER_CONFIRMATION — "Your order has been received"</td></tr>
 *   <tr><td>{@code payment.completed}</td><td>PAYMENT_SUCCESS — "Payment successful"</td></tr>
 *   <tr><td>{@code payment.failed}</td><td>PAYMENT_FAILURE — "Payment failed"</td></tr>
 *   <tr><td>{@code order.completed}</td><td>ORDER_CONFIRMATION — "Order confirmed"</td></tr>
 *   <tr><td>{@code order.cancelled}</td><td>ORDER_CANCELLED — "Order cancelled"</td></tr>
 *   <tr><td>{@code order.failed}</td><td>ORDER_FAILED — "Order could not be completed"</td></tr>
 * </table>
 *
 * <p>For each event, this class builds a human-readable subject and body string, then delegates
 * to {@link com.paymentplatform.notificationservice.application.service.NotificationService#sendNotification}.
 * Business logic (deduplication, retry, DLQ) lives in the service — this class only handles
 * JSON deserialisation and message construction.</p>
 *
 * <p>Manual acknowledgment ({@code AckMode.MANUAL_IMMEDIATE}) is used. Ack is sent only on success;
 * on failure a {@code RuntimeException} is thrown to let Resilience4j retry handle redelivery.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventConsumer {

    /** Service that creates notification records, routes to channels, and handles retry/DLQ. */
    private final NotificationService notificationService;

    /** Deserialises the raw Kafka JSON payload into typed event objects. */
    private final ObjectMapper objectMapper;

    /**
     * Handles {@code order.created} — sends an "Order Received" confirmation to the customer.
     *
     * @param payload raw JSON string from Kafka
     * @param ack     manual acknowledgment handle; ack'd only on success
     */
    @KafkaListener(
            topics = KafkaTopics.ORDER_CREATED,
            groupId = "notification-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleOrderCreated(String payload, Acknowledgment ack) {
        try {
            OrderCreatedEvent event = objectMapper.readValue(payload, OrderCreatedEvent.class);
            log.info("Received order.created: orderId={}", event.getOrderId());
            notificationService.sendNotification(
                    event.getCustomerId(),
                    event.getCustomerEmail(),
                    NotificationType.ORDER_CONFIRMATION,
                    "Order Received — #" + safeShortId(event.getOrderId()),
                    buildOrderCreatedBody(event),
                    buildReferenceId(KafkaTopics.ORDER_CREATED, event.getOrderId()),
                    KafkaTopics.ORDER_CREATED,
                    event.getEventId() != null ? event.getEventId().toString() : null
            );
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process order.created notification: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Handles {@code payment.completed} — sends a "Payment Successful" alert via email and SMS.
     *
     * @param payload raw JSON string from Kafka
     * @param ack     manual acknowledgment handle
     */
    @KafkaListener(
            topics = KafkaTopics.PAYMENT_COMPLETED,
            groupId = "notification-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handlePaymentCompleted(String payload, Acknowledgment ack) {
        try {
            PaymentCompletedEvent event = objectMapper.readValue(payload, PaymentCompletedEvent.class);
            log.info("Received payment.completed: orderId={}, paymentId={}", event.getOrderId(), event.getPaymentId());
            notificationService.sendNotification(
                    event.getCustomerId(),
                    event.getCustomerEmail(),
                    NotificationType.PAYMENT_SUCCESS,
                    "Payment Successful — Order #" + safeShortId(event.getOrderId()),
                    buildPaymentCompletedBody(event),
                    buildReferenceId(KafkaTopics.PAYMENT_COMPLETED, event.getOrderId()),
                    KafkaTopics.PAYMENT_COMPLETED,
                    event.getEventId() != null ? event.getEventId().toString() : null
            );
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process payment.completed notification: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Handles {@code payment.failed} — sends a "Payment Failed" alert via email and SMS.
     *
     * @param payload raw JSON string from Kafka
     * @param ack     manual acknowledgment handle
     */
    @KafkaListener(
            topics = KafkaTopics.PAYMENT_FAILED,
            groupId = "notification-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handlePaymentFailed(String payload, Acknowledgment ack) {
        try {
            PaymentFailedEvent event = objectMapper.readValue(payload, PaymentFailedEvent.class);
            log.info("Received payment.failed: orderId={}", event.getOrderId());
            notificationService.sendNotification(
                    event.getCustomerId(),
                    event.getCustomerEmail(),
                    NotificationType.PAYMENT_FAILURE,
                    "Payment Failed — Order #" + safeShortId(event.getOrderId()),
                    buildPaymentFailedBody(event),
                    buildReferenceId(KafkaTopics.PAYMENT_FAILED, event.getOrderId()),
                    KafkaTopics.PAYMENT_FAILED,
                    event.getEventId() != null ? event.getEventId().toString() : null
            );
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process payment.failed notification: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Handles {@code order.completed} — sends an "Order Confirmed" notification via email.
     *
     * @param payload raw JSON string from Kafka
     * @param ack     manual acknowledgment handle
     */
    @KafkaListener(
            topics = KafkaTopics.ORDER_COMPLETED,
            groupId = "notification-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleOrderCompleted(String payload, Acknowledgment ack) {
        try {
            OrderCompletedEvent event = objectMapper.readValue(payload, OrderCompletedEvent.class);
            log.info("Received order.completed: orderId={}", event.getOrderId());
            notificationService.sendNotification(
                    event.getCustomerId(),
                    event.getCustomerEmail(),
                    NotificationType.ORDER_CONFIRMATION,
                    "Order Confirmed — #" + safeShortId(event.getOrderId()),
                    buildOrderCompletedBody(event),
                    buildReferenceId(KafkaTopics.ORDER_COMPLETED, event.getOrderId()),
                    KafkaTopics.ORDER_COMPLETED,
                    event.getEventId() != null ? event.getEventId().toString() : null
            );
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process order.completed notification: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Handles {@code order.cancelled} — sends an "Order Cancelled" notification via email.
     *
     * @param payload raw JSON string from Kafka
     * @param ack     manual acknowledgment handle
     */
    @KafkaListener(
            topics = KafkaTopics.ORDER_CANCELLED,
            groupId = "notification-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleOrderCancelled(String payload, Acknowledgment ack) {
        try {
            OrderCancelledEvent event = objectMapper.readValue(payload, OrderCancelledEvent.class);
            log.info("Received order.cancelled: orderId={}", event.getOrderId());
            notificationService.sendNotification(
                    event.getCustomerId(),
                    event.getCustomerEmail(),
                    NotificationType.ORDER_CANCELLED,
                    "Order Cancelled — #" + safeShortId(event.getOrderId()),
                    buildOrderCancelledBody(event),
                    buildReferenceId(KafkaTopics.ORDER_CANCELLED, event.getOrderId()),
                    KafkaTopics.ORDER_CANCELLED,
                    event.getEventId() != null ? event.getEventId().toString() : null
            );
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process order.cancelled notification: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Handles {@code order.failed} — sends an "Order Failed" notification via email.
     * The body informs the customer that no payment was charged.
     *
     * @param payload raw JSON string from Kafka
     * @param ack     manual acknowledgment handle
     */
    @KafkaListener(
            topics = KafkaTopics.ORDER_FAILED,
            groupId = "notification-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleOrderFailed(String payload, Acknowledgment ack) {
        try {
            OrderFailedEvent event = objectMapper.readValue(payload, OrderFailedEvent.class);
            log.info("Received order.failed: orderId={}, failedStep={}", event.getOrderId(), event.getFailedStep());
            notificationService.sendNotification(
                    event.getCustomerId(),
                    event.getCustomerEmail(),
                    NotificationType.ORDER_FAILED,
                    "Order Failed — #" + safeShortId(event.getOrderId()),
                    buildOrderFailedBody(event),
                    buildReferenceId(KafkaTopics.ORDER_FAILED, event.getOrderId()),
                    KafkaTopics.ORDER_FAILED,
                    event.getEventId() != null ? event.getEventId().toString() : null
            );
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process order.failed notification: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    // ── Email body builders ──

    private String buildOrderCreatedBody(OrderCreatedEvent e) {
        return String.format("Your order #%s has been received. Total: %s %s. We're processing it now.",
                safeShortId(e.getOrderId()), e.getTotalAmount().getAmount(), e.getTotalAmount().getCurrency());
    }

    private String buildPaymentCompletedBody(PaymentCompletedEvent e) {
        return String.format("Payment of %s %s for order #%s was successful. Transaction ref: %s",
                e.getAmount().getAmount(), e.getAmount().getCurrency(),
                safeShortId(e.getOrderId()), e.getTransactionReference());
    }

    private String buildPaymentFailedBody(PaymentFailedEvent e) {
        return String.format("Payment of %s %s for order #%s failed. Reason: %s. Please try again.",
                e.getAttemptedAmount().getAmount(), e.getAttemptedAmount().getCurrency(),
                safeShortId(e.getOrderId()), e.getFailureReason());
    }

    private String buildOrderCompletedBody(OrderCompletedEvent e) {
        return String.format("Your order #%s is confirmed! Total: %s %s. Thank you for your purchase.",
                safeShortId(e.getOrderId()), e.getTotalAmount().getAmount(), e.getTotalAmount().getCurrency());
    }

    private String buildOrderCancelledBody(OrderCancelledEvent e) {
        return String.format("Your order #%s has been cancelled. Reason: %s",
                safeShortId(e.getOrderId()), e.getCancellationReason());
    }

    private String buildOrderFailedBody(OrderFailedEvent e) {
        return String.format("We're sorry, your order #%s could not be completed. Reason: %s. "
                        + "No payment has been charged.",
                safeShortId(e.getOrderId()), e.getFailureReason());
    }

    private String buildReferenceId(String topic, String orderId) {
        return topic + ":" + orderId;
    }

    /**
     * Returns the first 8 characters of orderId for use in notification subjects/bodies.
     * Guards against null orderId (malformed events) and IDs shorter than 8 characters.
     */
    private String safeShortId(String orderId) {
        if (orderId == null) return "unknown";
        return orderId.length() >= 8 ? orderId.substring(0, 8) : orderId;
    }
}
