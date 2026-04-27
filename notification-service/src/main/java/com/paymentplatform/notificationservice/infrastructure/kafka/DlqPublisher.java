package com.paymentplatform.notificationservice.infrastructure.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentplatform.commonlib.constants.KafkaTopics;
import com.paymentplatform.commonlib.events.NotificationDlqEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes dead-lettered notification events to the {@code notification.dlq} Kafka topic.
 *
 * <p>Called by {@code NotificationService} after a notification has exhausted all retry attempts
 * ({@code attemptCount >= maxAttempts}). The DLQ message carries the full original payload,
 * failure reason, attempt count, and timestamps — enough for a retry-worker or support engineer
 * to replay the notification without consulting the database.</p>
 *
 * <p><strong>No outbox pattern here:</strong> Unlike inventory/payment events, a failed DLQ publish
 * does not affect saga correctness. If Kafka is down when the DLQ publish is attempted, the
 * notification stays {@code DEAD_LETTERED} in the DB and can be replayed manually. The tradeoff
 * (potential missed DLQ message) is acceptable for an observability side-channel.</p>
 *
 * <p><strong>Consumers:</strong> A future {@code notification-retry-worker} service (not yet
 * implemented) would consume this topic and re-attempt delivery. Grafana can alert on DLQ lag.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DlqPublisher {

    /** Spring Kafka template for direct (non-outbox) publish to the DLQ topic. */
    private final KafkaTemplate<String, String> kafkaTemplate;

    /** Jackson mapper for serialising {@link NotificationDlqEvent} to JSON. */
    private final ObjectMapper objectMapper;

    /**
     * Serialises and publishes a {@link NotificationDlqEvent} to {@code notification.dlq}.
     * Uses the original event ID as the Kafka message key for partition routing and traceability.
     *
     * @param event the dead-letter event built from the exhausted notification
     */
    public void publishToDlq(NotificationDlqEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(KafkaTopics.NOTIFICATION_DLQ, event.getOriginalEventId(), payload)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish to DLQ: eventId={}, error={}",
                                    event.getOriginalEventId(), ex.getMessage());
                        } else {
                            log.info("Published to DLQ: eventId={}, offset={}",
                                    event.getOriginalEventId(),
                                    result.getRecordMetadata().offset());
                        }
                    });
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize DLQ event: {}", e.getMessage(), e);
        }
    }
}
