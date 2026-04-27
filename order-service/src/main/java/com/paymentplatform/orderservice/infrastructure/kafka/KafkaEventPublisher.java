package com.paymentplatform.orderservice.infrastructure.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Thin wrapper around {@link KafkaTemplate} that publishes pre-serialised JSON payloads
 * from the transactional outbox to Kafka in order-service.
 *
 * <p>Called exclusively by {@code OutboxProcessor} — not invoked directly by business logic.
 * Returns a {@code CompletableFuture} so the caller can block with {@code .get()} and detect
 * failures for retry tracking.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaEventPublisher {

    /** Spring Kafka template configured with string serialisers. */
    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * Publishes a pre-serialised JSON payload to the given Kafka topic.
     * Uses the aggregate ID (typically orderId) as the message key for partition affinity —
     * all events for the same order land on the same partition, preserving ordering.
     *
     * @param topic   Kafka topic name
     * @param key     message key (orderId) for partition routing
     * @param payload JSON string payload
     * @return future that resolves with the send result or fails with a Kafka exception
     */
    public CompletableFuture<SendResult<String, String>> publish(String topic, String key, String payload) {
        log.debug("Publishing to topic={}, key={}", topic, key);
        return kafkaTemplate.send(topic, key, payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish to topic={}, key={}: {}", topic, key, ex.getMessage());
                    } else {
                        log.info("Published to topic={}, key={}, offset={}",
                                topic, key, result.getRecordMetadata().offset());
                    }
                });
    }
}
