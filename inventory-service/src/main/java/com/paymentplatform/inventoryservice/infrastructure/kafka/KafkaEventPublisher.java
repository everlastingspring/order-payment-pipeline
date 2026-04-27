package com.paymentplatform.inventoryservice.infrastructure.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Thin wrapper around {@link KafkaTemplate} that publishes pre-serialised JSON payloads
 * from the transactional outbox to Kafka.
 *
 * <p>Called exclusively by {@code OutboxProcessor} — not invoked directly by business logic.
 * The outbox pattern ensures this publisher only receives events whose corresponding domain
 * transaction has already committed, preventing the dual-write problem.</p>
 *
 * <p>Returns a {@code CompletableFuture} so the caller ({@code OutboxProcessor}) can block
 * with {@code .get()} and detect Kafka failures for retry tracking.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaEventPublisher {

    /** Spring Kafka template configured with string key/value serialisers. */
    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * Publishes a raw JSON payload to the given Kafka topic with the specified key.
     *
     * @param topic   Kafka topic name (e.g. {@code "inventory.reserved"})
     * @param key     message key — typically the aggregate ID (orderId), used for partition routing
     * @param payload JSON string produced by {@code ObjectMapper.writeValueAsString()}
     * @return future that completes with the send result or fails with a Kafka exception
     */
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
