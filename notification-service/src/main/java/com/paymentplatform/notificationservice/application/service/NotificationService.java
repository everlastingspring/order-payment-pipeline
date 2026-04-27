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
import com.paymentplatform.notificationservice.infrastructure.sender.NotificationChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Core application service for notification-service.
 *
 * <p><strong>Responsibilities:</strong></p>
 * <ul>
 *   <li>Create and persist {@link Notification} records for each inbound Kafka event.</li>
 *   <li>Route delivery to the appropriate {@link NotificationChannel} using the Strategy pattern.</li>
 *   <li>Enforce deduplication so the same event does not send duplicate notifications
 *       under at-least-once Kafka delivery.</li>
 *   <li>Track delivery attempts and dead-letter notifications that exhaust retries.</li>
 *   <li>Write an audit log entry ({@link com.paymentplatform.notificationservice.domain.entity.NotificationLog})
 *       for every inbound event regardless of send outcome.</li>
 * </ul>
 *
 * <p><strong>Channel routing (Strategy pattern):</strong> All {@link NotificationChannel} beans are
 * injected as a list. On each send attempt, the service finds the first channel whose
 * {@code supports(type)} returns {@code true}. Adding a new channel (Push, WhatsApp) requires
 * only a new {@code @Component} — this class does not change.</p>
 *
 * <p><strong>Current channel priority:</strong> SMS ({@code PAYMENT_SUCCESS}, {@code PAYMENT_FAILURE})
 * takes priority over Email (all types) because SMS is registered first in the Spring context. For all
 * other notification types, Email is the only matching channel.</p>
 *
 * <p><strong>What this service does NOT do:</strong></p>
 * <ul>
 *   <li>It does not produce Kafka events (DLQ publish is a side-channel, not saga-related).</li>
 *   <li>It does not schedule retries — each inbound Kafka message triggers one attempt;
 *       Resilience4j within {@code NotificationSender} handles low-level retries.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    /** Repository for persisting and querying {@link Notification} records. */
    private final NotificationRepository notificationRepository;

    /** Repository for writing append-only audit log entries per inbound event. */
    private final NotificationLogRepository notificationLogRepository;

    /** Publishes dead-lettered notifications to {@code notification.dlq} for external monitoring. */
    private final DlqPublisher dlqPublisher;

    /** MapStruct mapper for converting entities to DTOs. */
    private final NotificationMapper notificationMapper;

    /**
     * All {@link NotificationChannel} beans discovered and injected by Spring.
     * Each channel declares which {@link com.paymentplatform.commonlib.enums.NotificationType}s it handles.
     * To add a new channel (Push/WhatsApp), create a new {@code @Component}; no changes needed here.
     */
    @Autowired
    private List<NotificationChannel> channels;

    /**
     * Maximum number of send attempts before a notification is dead-lettered.
     * Configurable via {@code notification.retry.max-attempts} (default 3).
     */
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

    /**
     * Delegates to the appropriate channel and handles the result.
     * Increments {@code attemptCount} via {@code markRetrying()}, finds the first supporting channel,
     * and either marks the notification SENT, FAILED (for retry), or DEAD_LETTERED (exhausted).
     *
     * @param notification the notification to send; must be persisted (has an ID)
     */
    private void attemptSend(Notification notification) {
        notification.markRetrying();

        NotificationChannel channel = channels.stream()
                .filter(c -> c.supports(notification.getType()))
                .findFirst()
                .orElseThrow(() -> new UnsupportedOperationException(
                        "No channel found for notification type: " + notification.getType()));

        boolean sent = channel.send(
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

    /**
     * Constructs a {@link NotificationDlqEvent} from the dead-lettered notification and
     * publishes it to {@code notification.dlq} for external monitoring or replay.
     *
     * @param notification the notification that has been dead-lettered
     */
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

    /**
     * Retrieves a notification by its UUID.
     *
     * @param notificationId UUID of the notification
     * @return the notification DTO
     * @throws com.paymentplatform.commonlib.exception.ResourceNotFoundException if not found
     */
    @Transactional(readOnly = true)
    public NotificationDto getNotification(UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", notificationId));
        return notificationMapper.toDto(notification);
    }

    /**
     * Returns paginated notification history for a customer, sorted newest first.
     *
     * @param customerId customer identifier
     * @param pageable   pagination and sorting parameters
     * @return page of notification DTOs
     */
    @Transactional(readOnly = true)
    public Page<NotificationDto> getNotificationsByCustomer(String customerId, Pageable pageable) {
        return notificationRepository.findByCustomerIdOrderByCreatedAtDesc(customerId, pageable)
                .map(notificationMapper::toDto);
    }
}
