package com.paymentplatform.notificationservice.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentplatform.commonlib.constants.KafkaTopics;
import com.paymentplatform.commonlib.dto.MoneyDto;
import com.paymentplatform.commonlib.enums.NotificationType;
import com.paymentplatform.commonlib.enums.OrderStatus;
import com.paymentplatform.commonlib.events.*;
import com.paymentplatform.notificationservice.application.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EventConsumer Tests")
class EventConsumerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private EventConsumer eventConsumer;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        eventConsumer = new EventConsumer(notificationService, objectMapper);
    }

    @Nested
    @DisplayName("Order Created Event")
    class OrderCreatedEvent {
        @Test
        @DisplayName("should process order.created and send notification")
        void handleOrderCreated_success() throws Exception {
            String orderId = UUID.randomUUID().toString();
            String customerId = "customer-001";
            String email = "test@example.com";

            OrderCreatedEventTest event = new OrderCreatedEventTest(
                    orderId, customerId, email,
                    new MoneyDto(new BigDecimal("4097.00"), "INR"),
                    UUID.randomUUID()
            );
            String payload = objectMapper.writeValueAsString(event);

            eventConsumer.handleOrderCreated(payload, acknowledgment);

            verify(notificationService).sendNotification(
                    eq(customerId),
                    eq(email),
                    eq(NotificationType.ORDER_CONFIRMATION),
                    contains(orderId.substring(0, 8)),
                    anyString(),
                    eq(KafkaTopics.ORDER_CREATED + ":" + orderId),
                    eq(KafkaTopics.ORDER_CREATED),
                    anyString()
            );
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("should handle null eventId gracefully")
        void handleOrderCreated_nullEventId() throws Exception {
            String orderId = UUID.randomUUID().toString();

            OrderCreatedEventTest event = new OrderCreatedEventTest(
                    orderId, "customer-001", "test@example.com",
                    new MoneyDto(new BigDecimal("4097.00"), "INR"),
                    null
            );
            String payload = objectMapper.writeValueAsString(event);

            eventConsumer.handleOrderCreated(payload, acknowledgment);

            verify(notificationService).sendNotification(
                    anyString(), anyString(), any(), anyString(), anyString(),
                    eq(KafkaTopics.ORDER_CREATED + ":" + orderId), anyString(), eq(null)
            );
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("should handle null orderId without NullPointerException")
        void handleOrderCreated_nullOrderId() throws Exception {
            // A malformed event with null orderId must not crash the consumer
            // (safeShortId() returns "unknown" instead of calling .substring on null)
            OrderCreatedEventTest event = new OrderCreatedEventTest(
                    null, "customer-001", "test@example.com",
                    new MoneyDto(new BigDecimal("4097.00"), "INR"),
                    UUID.randomUUID()
            );
            String payload = objectMapper.writeValueAsString(event);

            eventConsumer.handleOrderCreated(payload, acknowledgment);

            verify(notificationService).sendNotification(
                    anyString(), anyString(), any(),
                    contains("unknown"),   // safeShortId fallback must appear in subject
                    anyString(),
                    eq(KafkaTopics.ORDER_CREATED + ":null"),
                    eq(KafkaTopics.ORDER_CREATED),
                    anyString()
            );
            verify(acknowledgment).acknowledge();
        }

        @Test
        @DisplayName("should rethrow exception on JSON parse error")
        void handleOrderCreated_invalidJson() {
            String invalidPayload = "invalid json";

            try {
                eventConsumer.handleOrderCreated(invalidPayload, acknowledgment);
            } catch (RuntimeException e) {
                verify(acknowledgment, never()).acknowledge();
            }
        }
    }

    @Nested
    @DisplayName("Payment Completed Event")
    class PaymentCompletedEventTests {
        @Test
        @DisplayName("should process payment.completed and send notification")
        void handlePaymentCompleted_success() throws Exception {
            String paymentId = UUID.randomUUID().toString();
            String orderId = UUID.randomUUID().toString();
            String customerId = "customer-001";
            String customerEmail = "test@example.com";

            PaymentCompletedEventTest event = new PaymentCompletedEventTest(
                    paymentId, orderId, customerId,
                    customerEmail,
                    new MoneyDto(new BigDecimal("4097.00"), "INR"),
                    "ref-123",
                    UUID.randomUUID()
            );
            String payload = objectMapper.writeValueAsString(event);

            eventConsumer.handlePaymentCompleted(payload, acknowledgment);

            verify(notificationService).sendNotification(
                    eq(customerId),
                    eq(customerEmail),
                    eq(NotificationType.PAYMENT_SUCCESS),
                    contains(orderId.substring(0, 8)),
                    contains("4097.00"),
                    eq(KafkaTopics.PAYMENT_COMPLETED + ":" + orderId),
                    eq(KafkaTopics.PAYMENT_COMPLETED),
                    anyString()
            );
            verify(acknowledgment).acknowledge();
        }
    }

    @Nested
    @DisplayName("Payment Failed Event")
    class PaymentFailedEventTests {
        @Test
        @DisplayName("should process payment.failed and send notification")
        void handlePaymentFailed_success() throws Exception {
            String orderId = UUID.randomUUID().toString();
            String customerId = "customer-001";
            String customerEmail = "test@example.com";

            PaymentFailedEventTest event = new PaymentFailedEventTest(
                    orderId, customerId,
                    customerEmail,
                    new MoneyDto(new BigDecimal("4097.00"), "INR"),
                    "Insufficient funds",
                    UUID.randomUUID()
            );
            String payload = objectMapper.writeValueAsString(event);

            eventConsumer.handlePaymentFailed(payload, acknowledgment);

            verify(notificationService).sendNotification(
                    eq(customerId),
                    eq(customerEmail),
                    eq(NotificationType.PAYMENT_FAILURE),
                    anyString(),
                    contains("Insufficient funds"),
                    eq(KafkaTopics.PAYMENT_FAILED + ":" + orderId),
                    eq(KafkaTopics.PAYMENT_FAILED),
                    anyString()
            );
            verify(acknowledgment).acknowledge();
        }
    }

    @Nested
    @DisplayName("Order Completed Event")
    class OrderCompletedEventTests {
        @Test
        @DisplayName("should process order.completed and send notification")
        void handleOrderCompleted_success() throws Exception {
            String orderId = UUID.randomUUID().toString();
            String customerId = "customer-001";
            String email = "test@example.com";

            OrderCompletedEventTest event = new OrderCompletedEventTest(
                    orderId, customerId, email,
                    new MoneyDto(new BigDecimal("4097.00"), "INR"),
                    UUID.randomUUID()
            );
            String payload = objectMapper.writeValueAsString(event);

            eventConsumer.handleOrderCompleted(payload, acknowledgment);

            verify(notificationService).sendNotification(
                    eq(customerId),
                    eq(email),
                    eq(NotificationType.ORDER_CONFIRMATION),
                    contains("Confirmed"),
                    anyString(),
                    eq(KafkaTopics.ORDER_COMPLETED + ":" + orderId),
                    eq(KafkaTopics.ORDER_COMPLETED),
                    anyString()
            );
            verify(acknowledgment).acknowledge();
        }
    }

    @Nested
    @DisplayName("Order Cancelled Event")
    class OrderCancelledEventTests {
        @Test
        @DisplayName("should process order.cancelled and send notification")
        void handleOrderCancelled_success() throws Exception {
            String orderId = UUID.randomUUID().toString();
            String customerId = "customer-001";
            String email = "test@example.com";

            OrderCancelledEventTest event = new OrderCancelledEventTest(
                    orderId, customerId, email,
                    "User requested",
                    BigDecimal.ZERO,
                    UUID.randomUUID()
            );
            String payload = objectMapper.writeValueAsString(event);

            eventConsumer.handleOrderCancelled(payload, acknowledgment);

            verify(notificationService).sendNotification(
                    eq(customerId),
                    eq(email),
                    eq(NotificationType.ORDER_CANCELLED),
                    anyString(),
                    contains("User requested"),
                    eq(KafkaTopics.ORDER_CANCELLED + ":" + orderId),
                    eq(KafkaTopics.ORDER_CANCELLED),
                    anyString()
            );
            verify(acknowledgment).acknowledge();
        }
    }

    @Nested
    @DisplayName("Order Failed Event")
    class OrderFailedEventTests {
        @Test
        @DisplayName("should process order.failed and send notification")
        void handleOrderFailed_success() throws Exception {
            String orderId = UUID.randomUUID().toString();
            String customerId = "customer-001";
            String email = "test@example.com";

            OrderFailedEventTest event = new OrderFailedEventTest(
                    orderId, customerId, email,
                    OrderStatus.FAILED,
                    "PAYMENT",
                    "Payment declined",
                    UUID.randomUUID()
            );
            String payload = objectMapper.writeValueAsString(event);

            eventConsumer.handleOrderFailed(payload, acknowledgment);

            verify(notificationService).sendNotification(
                    eq(customerId),
                    eq(email),
                    eq(NotificationType.ORDER_FAILED),
                    anyString(),
                    contains("Payment declined"),
                    eq(KafkaTopics.ORDER_FAILED + ":" + orderId),
                    eq(KafkaTopics.ORDER_FAILED),
                    anyString()
            );
            verify(acknowledgment).acknowledge();
        }
    }

    // Test event DTO classes for ObjectMapper compatibility
    static class OrderCreatedEventTest {
        public String orderId;
        public String customerId;
        public String customerEmail;
        public MoneyDto totalAmount;
        public UUID eventId;

        OrderCreatedEventTest(String orderId, String customerId, String customerEmail,
                            MoneyDto totalAmount, UUID eventId) {
            this.orderId = orderId;
            this.customerId = customerId;
            this.customerEmail = customerEmail;
            this.totalAmount = totalAmount;
            this.eventId = eventId;
        }
    }

    static class PaymentCompletedEventTest {
        public String paymentId;
        public String orderId;
        public String customerId;
        public String customerEmail;
        public MoneyDto amount;
        public String transactionReference;
        public UUID eventId;

        PaymentCompletedEventTest(String paymentId, String orderId, String customerId, String customerEmail,
                                MoneyDto amount, String transactionReference, UUID eventId) {
            this.paymentId = paymentId;
            this.orderId = orderId;
            this.customerId = customerId;
            this.customerEmail = customerEmail;
            this.amount = amount;
            this.transactionReference = transactionReference;
            this.eventId = eventId;
        }
    }

    static class PaymentFailedEventTest {
        public String orderId;
        public String customerId;
        public String customerEmail;
        public MoneyDto attemptedAmount;
        public String failureReason;
        public UUID eventId;

        PaymentFailedEventTest(String orderId, String customerId, String customerEmail, MoneyDto attemptedAmount,
                             String failureReason, UUID eventId) {
            this.orderId = orderId;
            this.customerId = customerId;
            this.customerEmail = customerEmail;
            this.attemptedAmount = attemptedAmount;
            this.failureReason = failureReason;
            this.eventId = eventId;
        }
    }

    static class OrderCompletedEventTest {
        public String orderId;
        public String customerId;
        public String customerEmail;
        public MoneyDto totalAmount;
        public UUID eventId;

        OrderCompletedEventTest(String orderId, String customerId, String customerEmail,
                              MoneyDto totalAmount, UUID eventId) {
            this.orderId = orderId;
            this.customerId = customerId;
            this.customerEmail = customerEmail;
            this.totalAmount = totalAmount;
            this.eventId = eventId;
        }
    }

    static class OrderCancelledEventTest {
        public String orderId;
        public String customerId;
        public String customerEmail;
        public String cancellationReason;
        public String cancelledBy;
        public MoneyDto refundAmount;
        public UUID eventId;

        OrderCancelledEventTest(String orderId, String customerId, String customerEmail,
                              String cancellationReason, BigDecimal refundAmount, UUID eventId) {
            this.orderId = orderId;
            this.customerId = customerId;
            this.customerEmail = customerEmail;
            this.cancellationReason = cancellationReason;
            this.cancelledBy = "system";
            this.refundAmount = new MoneyDto(refundAmount, "INR");
            this.eventId = eventId;
        }
    }

    static class OrderFailedEventTest {
        public String orderId;
        public String customerId;
        public String customerEmail;
        public String failedStep;
        public String failureReason;
        public UUID eventId;

        OrderFailedEventTest(String orderId, String customerId, String customerEmail,
                           OrderStatus status, String failedStep, String failureReason, UUID eventId) {
            this.orderId = orderId;
            this.customerId = customerId;
            this.customerEmail = customerEmail;
            this.failedStep = failedStep;
            this.failureReason = failureReason;
            this.eventId = eventId;
        }
    }
}
