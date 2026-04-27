package com.paymentplatform.inventoryservice.infrastructure.outbox;

import com.paymentplatform.inventoryservice.domain.entity.OutboxEvent;
import com.paymentplatform.inventoryservice.domain.entity.OutboxStatus;
import com.paymentplatform.inventoryservice.domain.repository.OutboxEventRepository;
import com.paymentplatform.inventoryservice.infrastructure.kafka.KafkaEventPublisher;
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
 * Scheduled processor that polls {@code PENDING} outbox rows and publishes them to Kafka.
 *
 * <p><strong>Poll cycle (default 5 s):</strong> Fetches up to 50 {@code PENDING} events ordered by
 * {@code createdAt ASC} (oldest first). For each event, calls {@link KafkaEventPublisher#publish}
 * and blocks on the {@code CompletableFuture}. Success → {@code PUBLISHED}; failure → increments
 * {@code retryCount}. Once {@code retryCount >= MAX_RETRIES} (5), the event is marked {@code FAILED}.</p>
 *
 * <p><strong>Circuit breaker:</strong> The entire {@code processOutbox()} method is guarded by a
 * Resilience4j circuit breaker named {@code kafkaPublisher}. When the circuit is OPEN (Kafka is down),
 * the fallback logs a warning and skips the poll to avoid cascading failures.</p>
 *
 * <p><strong>At-least-once semantics:</strong> If the service crashes after Kafka acks but before
 * the DB update, the row stays {@code PENDING} and will be re-published on recovery. Consumers
 * must be idempotent (e.g. via the {@code IdempotencyKey} table in payment-service).</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxProcessor {

    /** Repository for reading PENDING events and updating their status after publish. */
    private final OutboxEventRepository outboxEventRepository;

    /** Kafka publisher that wraps {@code KafkaTemplate.send()}. */
    private final KafkaEventPublisher kafkaEventPublisher;

    /**
     * Maximum number of consecutive publish failures before an event is permanently marked FAILED.
     * Prevents indefinite retries of events with corrupt payloads or mismatched topics.
     */
    private static final int MAX_RETRIES = 5;

    /**
     * Main poll cycle — fetches up to 50 PENDING events and attempts to publish each one.
     * Runs every {@code outbox.processor.poll-interval-ms} milliseconds (default 5000).
     * Protected by the {@code kafkaPublisher} circuit breaker.
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
                        .get();

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

    /**
     * Fallback invoked when the {@code kafkaPublisher} circuit breaker is OPEN.
     * Logs a warning and returns without processing to avoid hammering a down Kafka cluster.
     *
     * @param e the exception that triggered the open-circuit transition
     */
    private void processOutboxFallback(CallNotPermittedException e) {
        log.warn("Outbox processing skipped — Kafka circuit breaker is OPEN: {}", e.getMessage());
    }

    /**
     * Generic fallback for unexpected exceptions during outbox processing.
     *
     * @param e the caught exception
     */
    private void processOutboxFallback(Exception e) {
        log.error("Outbox processing failed: {}", e.getMessage());
    }
}
