package com.paymentplatform.notificationservice.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only audit log of every Kafka event that triggered a notification attempt.
 *
 * <p>One row is written per {@code NotificationService.sendNotification()} call, regardless
 * of whether the send succeeded or failed. This gives an immutable record of what events
 * arrived, when, and which {@link Notification} they produced.</p>
 *
 * <p><strong>Use cases:</strong></p>
 * <ul>
 *   <li>Debugging — identify which source event caused a DEAD_LETTERED notification.</li>
 *   <li>Replay — re-process failed notifications by reading the original {@code payload}.</li>
 *   <li>Audit — answer "was the customer notified of event X?" without inspecting Kafka offsets.</li>
 * </ul>
 *
 * <p>Rows are never updated or deleted (append-only); there is no {@code @PreUpdate}.</p>
 */
@Entity
@Table(name = "notification_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationLog {

    /** Primary key — UUID assigned at persist time. */
    @Id
    private UUID id;

    /**
     * Foreign key to the {@link Notification} record created for this event.
     * May be null in the unlikely event the notification save failed before the log write.
     */
    @Column(name = "notification_id")
    private UUID notificationId;

    /**
     * Kafka topic name used as the event type label (e.g. {@code "order.created"}).
     * Mirrors the {@link com.paymentplatform.commonlib.constants.KafkaTopics} constants.
     */
    @Column(name = "event_type", nullable = false)
    private String eventType;

    /**
     * The {@code eventId} UUID from the source Kafka event ({@link com.paymentplatform.commonlib.events.BaseEvent#getEventId()}).
     * Used to correlate this log entry with distributed trace spans. May be null if the event
     * predates the {@code eventId} field.
     */
    @Column(name = "event_id")
    private String eventId;

    /**
     * Kafka topic from which the event was consumed. Redundant with {@link #eventType} in
     * current code but stored separately for forward compatibility with events that may carry
     * a different {@code eventType} header than their originating topic.
     */
    @Column(nullable = false)
    private String topic;

    /**
     * Raw JSON payload received from Kafka — the full serialised event body.
     * Stored as TEXT to allow replay without re-consuming the topic.
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    /** Immutable creation timestamp — set once in {@link #onCreate()}. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = Instant.now();
    }
}
