package com.paymentplatform.notificationservice.application.service;

import com.paymentplatform.commonlib.dto.NotificationDto;
import com.paymentplatform.commonlib.enums.NotificationStatus;
import com.paymentplatform.commonlib.enums.NotificationType;
import com.paymentplatform.commonlib.exception.ResourceNotFoundException;
import com.paymentplatform.notificationservice.application.mapper.NotificationMapper;
import com.paymentplatform.notificationservice.domain.entity.Notification;
import com.paymentplatform.notificationservice.domain.entity.NotificationLog;
import com.paymentplatform.notificationservice.domain.repository.NotificationLogRepository;
import com.paymentplatform.notificationservice.domain.repository.NotificationRepository;
import com.paymentplatform.notificationservice.infrastructure.kafka.DlqPublisher;
import com.paymentplatform.notificationservice.infrastructure.sender.NotificationSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService")
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationLogRepository notificationLogRepository;
    @Mock private NotificationSender sender;
    @Mock private DlqPublisher dlqPublisher;
    @Mock private NotificationMapper notificationMapper;

    private NotificationService notificationService;

    private static final String CUSTOMER_ID = "customer-001";
    private static final String EMAIL = "test@example.com";
    private static final String REFERENCE_ID = "order:" + UUID.randomUUID();

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(
                notificationRepository, notificationLogRepository,
                sender, dlqPublisher, notificationMapper);
        ReflectionTestUtils.setField(notificationService, "maxAttempts", 3);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void stubNotificationSave() {
        when(notificationRepository.save(any())).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            if (n.getId() == null) {
                setField(n, "id", UUID.randomUUID());
                setField(n, "createdAt", java.time.Instant.now());
                setField(n, "updatedAt", java.time.Instant.now());
            }
            return n;
        });
    }

    private void setField(Object target, String name, Object value) {
        try {
            var f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ── sendNotification ──────────────────────────────────────────────────

    @Nested
    @DisplayName("sendNotification")
    class SendNotification {

        @Test
        @DisplayName("happy path: creates notification, sends successfully, marks SENT")
        void happyPath_sendsAndMarksSent() {
            when(notificationRepository.existsByReferenceIdAndType(REFERENCE_ID, NotificationType.ORDER_CONFIRMATION))
                    .thenReturn(false);
            stubNotificationSave();
            when(sender.send(EMAIL, "Order Created", "Your order was created")).thenReturn(true);

            notificationService.sendNotification(
                    CUSTOMER_ID, EMAIL, NotificationType.ORDER_CONFIRMATION,
                    "Order Created", "Your order was created",
                    REFERENCE_ID, "order.created", "evt-001");

            // Notification saved at least twice (create + markSent)
            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository, atLeast(2)).save(captor.capture());
            Notification lastSave = captor.getAllValues().get(captor.getAllValues().size() - 1);
            assertThat(lastSave.getStatus()).isEqualTo(NotificationStatus.SENT);

            // Log entry saved
            verify(notificationLogRepository).save(any(NotificationLog.class));
        }

        @Test
        @DisplayName("duplicate notification: skips when referenceId+type already exists")
        void duplicate_skipsProcessing() {
            when(notificationRepository.existsByReferenceIdAndType(REFERENCE_ID, NotificationType.ORDER_CONFIRMATION))
                    .thenReturn(true);

            notificationService.sendNotification(
                    CUSTOMER_ID, EMAIL, NotificationType.ORDER_CONFIRMATION,
                    "Order Created", "body",
                    REFERENCE_ID, "order.created", "evt-001");

            verify(notificationRepository, never()).save(any());
            verify(sender, never()).send(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("null email: notification stays PENDING, sender not called")
        void nullEmail_staysPending() {
            when(notificationRepository.existsByReferenceIdAndType(REFERENCE_ID, NotificationType.ORDER_CONFIRMATION))
                    .thenReturn(false);
            stubNotificationSave();

            notificationService.sendNotification(
                    CUSTOMER_ID, null, NotificationType.ORDER_CONFIRMATION,
                    "Order Created", "body",
                    REFERENCE_ID, "order.created", "evt-001");

            verify(sender, never()).send(anyString(), anyString(), anyString());
            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository).save(captor.capture());
            assertThat(captor.getValue().getRecipientEmail()).isEqualTo("unknown");
        }

        @Test
        @DisplayName("send fails under maxAttempts: marks FAILED, no DLQ")
        void sendFails_underMax_marksFailed() {
            when(notificationRepository.existsByReferenceIdAndType(REFERENCE_ID, NotificationType.ORDER_CONFIRMATION))
                    .thenReturn(false);
            stubNotificationSave();
            when(sender.send(anyString(), anyString(), anyString())).thenReturn(false);

            notificationService.sendNotification(
                    CUSTOMER_ID, EMAIL, NotificationType.ORDER_CONFIRMATION,
                    "Subject", "body",
                    REFERENCE_ID, "order.created", "evt-001");

            // attemptCount is 1 after markRetrying(), maxAttempts=3 → still under
            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository, atLeast(2)).save(captor.capture());
            Notification lastSave = captor.getAllValues().get(captor.getAllValues().size() - 1);
            assertThat(lastSave.getStatus()).isEqualTo(NotificationStatus.FAILED);
            verify(dlqPublisher, never()).publishToDlq(any());
        }

        @Test
        @DisplayName("send fails at maxAttempts: marks DEAD_LETTERED, publishes to DLQ")
        void sendFails_atMax_deadLettersAndPublishesDlq() {
            when(notificationRepository.existsByReferenceIdAndType(REFERENCE_ID, NotificationType.ORDER_CONFIRMATION))
                    .thenReturn(false);
            // Set maxAttempts=1 so that a single markRetrying() (attemptCount 0→1) hits the threshold
            ReflectionTestUtils.setField(notificationService, "maxAttempts", 1);
            stubNotificationSave();
            when(sender.send(anyString(), anyString(), anyString())).thenReturn(false);

            notificationService.sendNotification(
                    CUSTOMER_ID, EMAIL, NotificationType.ORDER_CONFIRMATION,
                    "Subject", "body",
                    REFERENCE_ID, "order.created", "evt-001");

            // After markRetrying(): attemptCount=1 (>= maxAttempts=1) → dead letter
            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository, atLeast(2)).save(captor.capture());
            Notification lastSave = captor.getAllValues().get(captor.getAllValues().size() - 1);
            assertThat(lastSave.getStatus()).isEqualTo(NotificationStatus.DEAD_LETTERED);
            verify(dlqPublisher).publishToDlq(any());
        }

        @Test
        @DisplayName("notification log is saved with source topic and event ID")
        void logsSourceEvent() {
            when(notificationRepository.existsByReferenceIdAndType(REFERENCE_ID, NotificationType.ORDER_CONFIRMATION))
                    .thenReturn(false);
            stubNotificationSave();
            when(sender.send(anyString(), anyString(), anyString())).thenReturn(true);

            notificationService.sendNotification(
                    CUSTOMER_ID, EMAIL, NotificationType.ORDER_CONFIRMATION,
                    "Subject", "body",
                    REFERENCE_ID, "order.created", "evt-001");

            ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
            verify(notificationLogRepository).save(captor.capture());
            NotificationLog log = captor.getValue();
            assertThat(log.getEventType()).isEqualTo("order.created");
            assertThat(log.getEventId()).isEqualTo("evt-001");
            assertThat(log.getTopic()).isEqualTo("order.created");
        }
    }

    // ── getNotification ───────────────────────────────────────────────────

    @Nested
    @DisplayName("getNotification")
    class GetNotification {

        @Test
        @DisplayName("returns mapped DTO when notification found")
        void found_returnsMappedDto() {
            UUID notifId = UUID.randomUUID();
            Notification notification = Notification.builder()
                    .customerId(CUSTOMER_ID).recipientEmail(EMAIL)
                    .type(NotificationType.ORDER_CONFIRMATION).subject("Subject").build();
            setField(notification, "id", notifId);
            NotificationDto dto = NotificationDto.builder().notificationId(notifId.toString()).build();

            when(notificationRepository.findById(notifId)).thenReturn(Optional.of(notification));
            when(notificationMapper.toDto(notification)).thenReturn(dto);

            assertThat(notificationService.getNotification(notifId)).isEqualTo(dto);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when notification not found")
        void notFound_throwsResourceNotFoundException() {
            UUID notifId = UUID.randomUUID();
            when(notificationRepository.findById(notifId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> notificationService.getNotification(notifId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── getNotificationsByCustomer ────────────────────────────────────────

    @Nested
    @DisplayName("getNotificationsByCustomer")
    class GetNotificationsByCustomer {

        @Test
        @DisplayName("returns paged and mapped results")
        void returnsMappedPage() {
            Pageable pageable = PageRequest.of(0, 10);
            Notification notification = Notification.builder()
                    .customerId(CUSTOMER_ID).recipientEmail(EMAIL)
                    .type(NotificationType.ORDER_CONFIRMATION).subject("Subject").build();
            setField(notification, "id", UUID.randomUUID());
            NotificationDto dto = NotificationDto.builder().build();

            when(notificationRepository.findByCustomerIdOrderByCreatedAtDesc(CUSTOMER_ID, pageable))
                    .thenReturn(new PageImpl<>(List.of(notification)));
            when(notificationMapper.toDto(notification)).thenReturn(dto);

            Page<NotificationDto> result = notificationService.getNotificationsByCustomer(CUSTOMER_ID, pageable);

            assertThat(result.getContent()).hasSize(1).containsExactly(dto);
        }

        @Test
        @DisplayName("returns empty page when customer has no notifications")
        void noNotifications_returnsEmptyPage() {
            Pageable pageable = PageRequest.of(0, 10);
            when(notificationRepository.findByCustomerIdOrderByCreatedAtDesc("unknown", pageable))
                    .thenReturn(Page.empty());

            Page<NotificationDto> result = notificationService.getNotificationsByCustomer("unknown", pageable);

            assertThat(result.getContent()).isEmpty();
        }
    }
}
