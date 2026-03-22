package com.paymentplatform.orderservice.infrastructure.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * Publishes a pre-serialized JSON payload to the given Kafka topic.
     * Uses the aggregateId as the message key for partition affinity.
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
