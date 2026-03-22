package com.paymentplatform.notificationservice.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Audit trail of every event that triggered a notification.
 * Useful for debugging and replaying failed notifications.
 */
@Entity
@Table(name = "notification_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationLog {

    @Id
    private UUID id;

    @Column(name = "notification_id")
    private UUID notificationId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "event_id")
    private String eventId;

    @Column(nullable = false)
    private String topic;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = Instant.now();
    }
}
