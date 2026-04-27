package com.paymentplatform.commonlib.enums;

/**
 * Delivery states of a notification record in notification_db.
 *
 * <p>Typical flow: {@code PENDING → RETRYING → SENT}</p>
 * <p>Failure flow: {@code PENDING → RETRYING → FAILED → DEAD_LETTERED}</p>
 *
 * <p>After {@code maxAttempts} consecutive failures, the notification is
 * dead-lettered and a {@code NotificationDlqEvent} is published to the DLQ topic.</p>
 */
public enum NotificationStatus {

    /** Created but not yet attempted. */
    PENDING,

    /** Successfully delivered to the recipient. Terminal state. */
    SENT,

    /**
     * Last attempt failed; not yet exhausted retries.
     * The record stays in this state until the next retry attempt.
     */
    FAILED,

    /** Currently being retried (attempt in progress). */
    RETRYING,

    /**
     * All retry attempts exhausted — published to notification.dlq.
     * Requires manual intervention or DLQ worker processing.
     * Terminal state.
     */
    DEAD_LETTERED
}
