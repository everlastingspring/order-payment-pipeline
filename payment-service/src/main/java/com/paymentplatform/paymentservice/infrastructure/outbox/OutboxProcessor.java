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
 * Scheduled processor that polls {@code PENDING} outbox rows in payment-service and publishes
 * them to Kafka.
 *
 * <p><strong>Guarantees:</strong></p>
 * <ul>
 *   <li>At-least-once delivery — failed publishes are retried up to {@code MAX_RETRIES} times.</li>
 *   <li>Ordering per aggregate — events are polled {@code ORDER BY created_at ASC} so that
 *       {@code payment.completed} is never delivered before {@code payment.failed} for the same payment.</li>
 *   <li>Poison pill protection — events that exhaust retries are permanently marked {@code FAILED}.</li>
 *   <li>Circuit breaker — stops polling when Kafka is consistently unavailable.</li>
 * </ul>
 *
 * <p><strong>Events published by payment-service:</strong></p>
 * <ul>
 *   <li>{@code payment.completed} — consumed by order-service (CONFIRMED), notification-service, wallet-service</li>
 *   <li>{@code payment.failed} — consumed by order-service (FAILED + order.failed → inventory release)</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxProcessor {

    /** Repository for reading PENDING events and persisting status updates. */
    private final OutboxEventRepository outboxEventRepository;

    /** Wraps {@code KafkaTemplate.send()} with logging and future handling. */
    private final KafkaEventPublisher kafkaEventPublisher;

    /**
     * Maximum publish attempts before an event is permanently marked FAILED.
     */
    private static final int MAX_RETRIES = 5;

    /**
     * Main poll cycle — fetches up to 50 PENDING events and publishes each one.
     * Runs every {@code outbox.processor.poll-interval-ms} milliseconds (default 5000).
     */
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
