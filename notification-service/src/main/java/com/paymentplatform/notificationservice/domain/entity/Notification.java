package com.paymentplatform.notificationservice.domain.entity;

import com.paymentplatform.commonlib.enums.NotificationStatus;
import com.paymentplatform.commonlib.enums.NotificationType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a notification record in notification_db.
 *
 * <p>A notification is created by {@code NotificationService.sendNotification()} for every
 * relevant Kafka event (order created, payment succeeded/failed, order cancelled/failed).
 * The entity tracks delivery state from {@code PENDING} through {@code SENT} or, on failure,
 * through {@code RETRYING} → {@code FAILED} → {@code DEAD_LETTERED}.</p>
 *
 * <p><strong>Deduplication:</strong> The {@code referenceId} field (format {@code "topic:orderId"})
 * combined with {@code type} prevents sending the same notification twice for the same Kafka event.
 * This handles at-least-once Kafka delivery without double-notifying the customer.</p>
 *
 * <p><strong>Unknown email handling:</strong> If the source event does not carry {@code customerEmail}
 * (e.g. some payment events), {@code recipientEmail} is stored as {@code "unknown"} and the
 * notification stays {@code PENDING} for manual resolution rather than silently dropping it.</p>
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    /** Primary key — UUID assigned at persist time. */
    @Id
    private UUID id;

    /** The customer who triggered this notification (e.g. the order's customerId). */
    @Column(name = "customer_id", nullable = false)
    private String customerId;

    /**
     * Email address the notification was sent (or attempted) to.
     * Set to {@code "unknown"} when the source event does not carry an email — see class-level note.
     */
    @Column(name = "recipient_email", nullable = false)
    private String recipientEmail;

    /**
     * Categorises the notification for channel routing and deduplication.
     * See {@link NotificationType} for all values and which channels handle each.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    /**
     * Current delivery state.
     * Starts as {@code PENDING}; transitions via {@link #markRetrying()}, {@link #markSent()},
     * {@link #markFailed()}, or {@link #markDeadLettered()}.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private NotificationStatus status = NotificationStatus.PENDING;

    /**
     * Short summary line displayed in the email subject or SMS preview.
     * Length limited to 500 characters to fit most email client subject lines.
     */
    @Column(nullable = false, length = 500)
    private String subject;

    /**
     * Full notification body — plain-text message built by {@code EventConsumer} body builders.
     * Stored as TEXT (unbounded) to accommodate future rich-format (HTML) notifications.
     */
    @Column(columnDefinition = "TEXT")
    private String body;

    /**
     * Deduplication key — format {@code "topic:orderId"} (e.g. {@code "order.created:abc123"}).
     * Combined with {@link #type}, uniquely identifies whether this notification was already sent
     * for the same source event, protecting against Kafka at-least-once redelivery.
     */
    @Column(name = "reference_id")
    private String referenceId;

    /**
     * Number of send attempts made. Incremented by {@link #markRetrying()}.
     * When {@code attemptCount >= maxAttempts} ({@code notification.retry.max-attempts}, default 3),
     * the notification is dead-lettered and published to {@code notification.dlq}.
     */
    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private int attemptCount = 0;

    /** Immutable creation timestamp — set once in {@link #onCreate()}. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Timestamp of successful delivery. Null until {@link #markSent()} is called.
     */
    @Column(name = "sent_at")
    private Instant sentAt;

    /** Last-updated timestamp — auto-refreshed by {@link #onUpdate()}. */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Transitions to {@code SENT} and records the delivery timestamp.
     * Called after a {@link NotificationChannel} returns {@code true}.
     */
    public void markSent() {
        this.status = NotificationStatus.SENT;
        this.sentAt = Instant.now();
    }

    /**
     * Transitions to {@code FAILED}. Called when a send attempt fails
     * but {@link #attemptCount} has not yet reached {@code maxAttempts}.
     */
    public void markFailed() {
        this.status = NotificationStatus.FAILED;
    }

    /**
     * Transitions to {@code RETRYING} and increments the attempt counter.
     * Called at the start of each send attempt before delegating to the channel.
     */
    public void markRetrying() {
        this.status = NotificationStatus.RETRYING;
        this.attemptCount++;
    }

    /**
     * Transitions to {@code DEAD_LETTERED} (terminal). Called after {@code maxAttempts}
     * consecutive failures; triggers a DLQ publish via {@link com.paymentplatform.notificationservice.infrastructure.kafka.DlqPublisher}.
     */
    public void markDeadLettered() {
        this.status = NotificationStatus.DEAD_LETTERED;
    }
}
