package com.paymentplatform.commonlib.events;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.paymentplatform.commonlib.constants.KafkaTopics;
import com.paymentplatform.commonlib.enums.NotificationType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * Published to {@code notification.dlq} when a notification exhausts all retry attempts.
 *
 * <p>A notification arrives here after {@code maxAttempts} (default 3) consecutive
 * send failures. The DLQ payload preserves the full original context so a
 * retry-worker can re-process or a human operator can inspect what went wrong.</p>
 *
 * <p>Consumer: a DLQ retry-worker (not yet implemented — see Phase 5 backlog).</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificationDlqEvent extends BaseEvent {

    /** The Kafka topic the original triggering event came from (e.g. "order.created"). */
    private String originalTopic;

    /** {@code eventId} of the original domain event, for correlation. */
    private String originalEventId;

    /** Event type of the original domain event (e.g. "order.created"). */
    private String originalEventType;

    /** The address that could not be reached. */
    private String recipientEmail;

    /** The notification type that failed (e.g. ORDER_CONFIRMATION). */
    private NotificationType notificationType;

    /** Last failure message from the send attempt. */
    private String failureReason;

    /** Total number of send attempts made before dead-lettering. */
    private int attemptCount;

    /** Timestamp of the very first send attempt. */
    private Instant firstAttemptAt;

    /** Timestamp of the most recent (final) failed attempt. */
    private Instant lastAttemptAt;

    /**
     * The email body that could not be delivered.
     * Preserved so the DLQ worker can re-send without re-constructing it.
     */
    private String rawPayload;

    public NotificationDlqEvent(String originalTopic, String originalEventId, String originalEventType,
                                 String recipientEmail, NotificationType notificationType,
                                 String failureReason, int attemptCount,
                                 Instant firstAttemptAt, String rawPayload) {
        super(KafkaTopics.NOTIFICATION_DLQ);
        this.originalTopic = originalTopic;
        this.originalEventId = originalEventId;
        this.originalEventType = originalEventType;
        this.recipientEmail = recipientEmail;
        this.notificationType = notificationType;
        this.failureReason = failureReason;
        this.attemptCount = attemptCount;
        this.firstAttemptAt = firstAttemptAt;
        this.lastAttemptAt = Instant.now();
        this.rawPayload = rawPayload;
    }
}
