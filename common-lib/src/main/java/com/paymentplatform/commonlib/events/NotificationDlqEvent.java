package com.paymentplatform.commonlib.events;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.paymentplatform.commonlib.constants.KafkaTopics;
import com.paymentplatform.commonlib.enums.NotificationType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificationDlqEvent extends BaseEvent {

    private String originalTopic;
    private String originalEventId;
    private String originalEventType;
    private String recipientEmail;
    private NotificationType notificationType;
    private String failureReason;
    private int attemptCount;
    private Instant firstAttemptAt;
    private Instant lastAttemptAt;
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
