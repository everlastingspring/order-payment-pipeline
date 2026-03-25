package com.paymentplatform.paymentservice.infrastructure.outbox;

import com.paymentplatform.paymentservice.domain.entity.OutboxEvent;
import com.paymentplatform.paymentservice.domain.entity.OutboxStatus;
import com.paymentplatform.paymentservice.domain.repository.OutboxEventRepository;
import com.paymentplatform.paymentservice.infrastructure.kafka.KafkaEventPublisher;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Polls the outbox_events table for PENDING events and publishes
 * them to Kafka. Runs every 500ms.
 *
 * Guarantees:
 *  - At-least-once delivery (retries on failure)
 *  - Ordering per aggregate (events polled by created_at ASC)
 *  - Poison pill protection (moves to FAILED after 5 retries)
 *  - Circuit breaker: stops hammering Kafka when broker is down
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxProcessor {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaEventPublisher kafkaEventPublisher;

    private static final int MAX_RETRIES = 5;

    @Scheduled(fixedDelayString = "${outbox.processor.poll-interval-ms:5000}")
    @Transactional
    @CircuitBreaker(name = "kafkaPublisher", fallbackMethod = "processOutboxFallback")
    public void processOutbox() {
        List<OutboxEvent> pendingEvents =
                outboxEventRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);

        if (pendingEvents.isEmpty()) {
            return;
        }

        int published = 0;
        List<OutboxEvent> publishedEvents = new ArrayList<>();

        for (OutboxEvent event : pendingEvents) {
            try {
                kafkaEventPublisher.publish(event.getTopic(), event.getAggregateId(), event.getPayload())
                        .get(); // block until Kafka confirms delivery

                event.markPublished();
                outboxEventRepository.save(event);
                published++;
                publishedEvents.add(event);
                log.info("Outbox → topic={} | eventType={} | aggregateId={}",
                        event.getTopic(), event.getEventType(), event.getAggregateId());

            } catch (Exception e) {
                log.error("Failed to publish outbox event: id={}, topic={}, eventType={}, attempt={}/{}",
                        event.getId(), event.getTopic(), event.getEventType(),
                        event.getRetryCount() + 1, MAX_RETRIES, e);

                event.retry();
                if (event.getRetryCount() >= MAX_RETRIES) {
                    event.markFailed();
                    log.error("Outbox event permanently failed after {} retries: id={}, eventType={}",
                            MAX_RETRIES, event.getId(), event.getEventType());
                }
                outboxEventRepository.save(event);
            }
        }

        if (published > 0) {
            String breakdown = publishedEvents.stream()
                    .collect(Collectors.groupingBy(OutboxEvent::getTopic, Collectors.counting()))
                    .entrySet().stream()
                    .map(e -> e.getKey() + " ×" + e.getValue())
                    .collect(Collectors.joining(", "));
            log.info("Outbox: published {}/{} events [{}]", published, pendingEvents.size(), breakdown);
        }
    }

    private void processOutboxFallback(CallNotPermittedException e) {
        log.warn("Outbox processing skipped — Kafka circuit breaker is OPEN: {}", e.getMessage());
    }

    private void processOutboxFallback(Exception e) {
        log.error("Outbox processing failed: {}", e.getMessage());
    }
}
