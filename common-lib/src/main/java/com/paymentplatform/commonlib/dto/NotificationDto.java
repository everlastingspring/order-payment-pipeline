package com.paymentplatform.commonlib.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.paymentplatform.commonlib.enums.NotificationStatus;
import com.paymentplatform.commonlib.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * API transfer object representing a notification record.
 *
 * <p>Returned by {@code GET /api/notifications/{notificationId}} and
 * {@code GET /api/notifications/customer/{customerId}}. Omits the full notification body
 * ({@code body} field from the entity) to keep API responses lightweight — callers do not
 * need the rendered message text, only the delivery metadata.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificationDto {

    /** UUID of the notification record. */
    private String notificationId;

    /** Customer the notification was sent to. */
    private String customerId;

    /** Email address the notification was (or should be) sent to. */
    private String recipientEmail;

    /** Notification category — determines channel routing and deduplication. */
    private NotificationType type;

    /** Current delivery status (PENDING → SENT or DEAD_LETTERED). */
    private NotificationStatus status;

    /** Short subject line (email subject or SMS preview). */
    private String subject;

    /**
     * Number of send attempts made. Starts at 0 (PENDING); incremented by
     * {@code Notification.markRetrying()} on each attempt. Dead-lettered at {@code maxAttempts}.
     */
    private int attemptCount;

    /** When the notification was created. */
    private Instant createdAt;

    /** When the notification was successfully delivered. Null until SENT. */
    private Instant sentAt;
}
