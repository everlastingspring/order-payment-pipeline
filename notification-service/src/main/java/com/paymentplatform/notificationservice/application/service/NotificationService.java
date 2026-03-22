package com.paymentplatform.notificationservice.application.service;

import com.paymentplatform.commonlib.dto.NotificationDto;
import com.paymentplatform.commonlib.enums.NotificationStatus;
import com.paymentplatform.commonlib.enums.NotificationType;
import com.paymentplatform.commonlib.events.NotificationDlqEvent;
import com.paymentplatform.commonlib.exception.ResourceNotFoundException;
import com.paymentplatform.notificationservice.application.mapper.NotificationMapper;
import com.paymentplatform.notificationservice.domain.entity.Notification;
import com.paymentplatform.notificationservice.domain.entity.NotificationLog;
import com.paymentplatform.notificationservice.domain.repository.NotificationLogRepository;
import com.paymentplatform.notificationservice.domain.repository.NotificationRepository;
import com.paymentplatform.notificationservice.infrastructure.kafka.DlqPublisher;
import com.paymentplatform.notificationservice.infrastructure.sender.NotificationSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final NotificationSender sender;
    private final DlqPublisher dlqPublisher;
    private final NotificationMapper notificationMapper;

    @Value("${notification.retry.max-attempts:3}")
    private int maxAttempts;

    /**
     * Creates a notification, attempts to send it, and logs the event.
     * If the recipient email is null (e.g., payment events don't carry it),
     * we store "unknown" and the notification goes to PENDING for manual resolution.
     */
    @Transactional
    public void sendNotification(String customerId, String recipientEmail,
                                 NotificationType type, String subject, String body,
                                 String referenceId, String sourceTopic, String sourceEventId) {

        String email = recipientEmail != null ? recipientEmail : "unknown";

        // Deduplication: skip if we already sent this exact notification type for this reference
        if (notificationRepository.existsByReferenceIdAndType(referenceId, type)) {
            log.info("Duplicate notification skipped: referenceId={}, type={}", referenceId, type);
            return;
        }

        // Create notification record
        Notification notification = Notification.builder()
                .customerId(customerId)
                .recipientEmail(email)
                .type(type)
                .status(NotificationStatus.PENDING)
                .subject(subject)
                .body(body)
                .referenceId(referenceId)
                .build();
        notification = notificationRepository.save(notification);

        // Log the source event
        NotificationLog logEntry = NotificationLog.builder()
                .notificationId(notification.getId())
                .eventType(sourceTopic)
                .eventId(sourceEventId)
                .topic(sourceTopic)
                .payload(body)
                .build();
        notificationLogRepository.save(logEntry);

        // Attempt to send
        if (!"unknown".equals(email)) {
            attemptSend(notification);
        } else {
            log.warn("No recipient email — notification {} stays PENDING for manual resolution",
                    notification.getId());
        }
    }

    private void attemptSend(Notification notification) {
        notification.markRetrying();

        boolean sent = sender.send(
                notification.getRecipientEmail(),
                notification.getSubject(),
                notification.getBody()
        );

        if (sent) {
            notification.markSent();
            notificationRepository.save(notification);
            log.info("Notification sent: id={}, type={}, to={}",
                    notification.getId(), notification.getType(), notification.getRecipientEmail());
        } else if (notification.getAttemptCount() >= maxAttempts) {
            // Exhausted retries — dead letter
            notification.markDeadLettered();
            notificationRepository.save(notification);
            log.error("Notification dead-lettered after {} attempts: id={}",
                    maxAttempts, notification.getId());

            publishToDlq(notification);
        } else {
            notification.markFailed();
            notificationRepository.save(notification);
            log.warn("Notification send failed (attempt {}/{}): id={}",
                    notification.getAttemptCount(), maxAttempts, notification.getId());
        }
    }

    private void publishToDlq(Notification notification) {
        NotificationDlqEvent dlqEvent = NotificationDlqEvent.builder()
                .originalTopic(notification.getType().name())
                .originalEventId(notification.getReferenceId())
                .originalEventType(notification.getType().name())
                .recipientEmail(notification.getRecipientEmail())
                .notificationType(notification.getType())
                .failureReason("Exhausted " + maxAttempts + " send attempts")
                .attemptCount(notification.getAttemptCount())
                .firstAttemptAt(notification.getCreatedAt())
                .lastAttemptAt(Instant.now())
                .rawPayload(notification.getBody())
                .build();

        dlqPublisher.publishToDlq(dlqEvent);
    }

    // ── Read operations ──

    @Transactional(readOnly = true)
    public NotificationDto getNotification(UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", notificationId));
        return notificationMapper.toDto(notification);
    }

    @Transactional(readOnly = true)
    public Page<NotificationDto> getNotificationsByCustomer(String customerId, Pageable pageable) {
        return notificationRepository.findByCustomerIdOrderByCreatedAtDesc(customerId, pageable)
                .map(notificationMapper::toDto);
    }
}
