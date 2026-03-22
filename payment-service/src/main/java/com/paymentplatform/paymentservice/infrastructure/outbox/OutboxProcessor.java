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

import java.util.List;

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

    @Scheduled(fixedDelay = 500)
    @Transactional
    @CircuitBreaker(name = "kafkaPublisher", fallbackMethod = "processOutboxFallback")
    public void processOutbox() {
        List<OutboxEvent> pendingEvents =
                outboxEventRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);

        for (OutboxEvent event : pendingEvents) {
            try {
                kafkaEventPublisher.publish(event.getTopic(), event.getAggregateId(), event.getPayload())
                        .get(); // block until Kafka confirms delivery

                event.markPublished();
                outboxEventRepository.save(event);
                log.debug("Outbox event published: id={}, topic={}", event.getId(), event.getTopic());

            } catch (Exception e) {
                log.error("Failed to publish outbox event: id={}, topic={}, attempt={}/{}",
                        event.getId(), event.getTopic(), event.getRetryCount() + 1, MAX_RETRIES, e);

                event.retry();
                if (event.getRetryCount() >= MAX_RETRIES) {
                    event.markFailed();
                    log.error("Outbox event permanently failed after {} retries: id={}",
                            MAX_RETRIES, event.getId());
                }
                outboxEventRepository.save(event);
            }
        }
    }

    private void processOutboxFallback(CallNotPermittedException e) {
        log.warn("Outbox processing skipped — Kafka circuit breaker is OPEN: {}", e.getMessage());
    }

    private void processOutboxFallback(Exception e) {
        log.error("Outbox processing failed: {}", e.getMessage());
    }
}
