package com.paymentplatform.orderservice.infrastructure.outbox;

import com.paymentplatform.orderservice.domain.entity.OutboxEvent;
import com.paymentplatform.orderservice.domain.entity.OutboxStatus;
import com.paymentplatform.orderservice.domain.repository.OutboxEventRepository;
import com.paymentplatform.orderservice.infrastructure.kafka.KafkaEventPublisher;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxProcessor {

    private static final int MAX_RETRY_COUNT = 5;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaEventPublisher kafkaEventPublisher;

    /**
     * Polls PENDING outbox events every 500ms and publishes them to Kafka.
     * On success, marks as PUBLISHED. On failure, increments retry count.
     * Events exceeding MAX_RETRY_COUNT are marked FAILED permanently.
     *
     * Circuit breaker: stops polling Kafka when it's consistently failing,
     * preventing the 500ms scheduler from hammering a dead broker.
     */
    @Scheduled(fixedDelay = 500)
    @Transactional
    @CircuitBreaker(name = "kafkaPublisher", fallbackMethod = "processOutboxFallback")
    public void processOutbox() {
        List<OutboxEvent> events = outboxEventRepository
                .findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);

        for (OutboxEvent event : events) {
            try {
                kafkaEventPublisher.publish(event.getTopic(), event.getAggregateId(), event.getPayload())
                        .get(); // block to ensure delivery before marking published
                event.markPublished();
                outboxEventRepository.save(event);
            } catch (Exception e) {
                log.error("Failed to publish outbox event: id={}, topic={}, attempt={}",
                        event.getId(), event.getTopic(), event.getRetryCount() + 1, e);
                event.markFailed();
                if (event.getRetryCount() >= MAX_RETRY_COUNT) {
                    log.error("Outbox event permanently failed after {} retries: id={}",
                            MAX_RETRY_COUNT, event.getId());
                } else {
                    event.retry(); // reset to PENDING for next poll
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
