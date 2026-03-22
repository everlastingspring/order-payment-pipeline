package com.paymentplatform.paymentservice.infrastructure.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Thin wrapper around KafkaTemplate.
 * Sends pre-serialized JSON strings (from the outbox) to Kafka topics.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public CompletableFuture<SendResult<String, String>> publish(String topic, String key, String payload) {
        log.debug("Publishing to topic={}, key={}", topic, key);

        CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(topic, key, payload);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish to topic={}, key={}: {}", topic, key, ex.getMessage());
            } else {
                log.info("Published to topic={}, key={}, offset={}",
                        topic, key, result.getRecordMetadata().offset());
            }
        });

        return future;
    }
}
