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
 * Publishes to the notification.dlq topic when a notification
 * exhausts all retry attempts. DLQ messages can be picked up
 * by a retry-worker or monitored via alerting.
 *
 * No outbox needed here — a failed DLQ publish just means the
 * notification stays in FAILED status in the DB and can be
 * retried manually.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DlqPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

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
