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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Scheduled processor that polls {@code PENDING} outbox rows in order-service and publishes
 * them to Kafka.
 *
 * <p><strong>Poll cycle (default 5 s):</strong> Fetches up to 50 {@code PENDING} events ordered
 * by {@code createdAt ASC}. For each event, calls {@link KafkaEventPublisher#publish} and blocks
 * on the {@code CompletableFuture}. On success → {@code PUBLISHED}. On failure →
 * {@code markFailed()} and then {@code retry()} (back to {@code PENDING}) until
 * {@code MAX_RETRY_COUNT} (5) is reached, at which point the event stays {@code FAILED}.</p>
 *
 * <p><strong>Events published by order-service:</strong></p>
 * <ul>
 *   <li>{@code order.created} — saga entry point; consumed by inventory-service and notification-service</li>
 *   <li>{@code order.completed} — consumed by notification-service</li>
 *   <li>{@code order.failed} — consumed by inventory-service (release stock) and notification-service</li>
 *   <li>{@code order.cancelled} — consumed by inventory-service, notification-service, wallet-service</li>
 * </ul>
 *
 * <p><strong>Circuit breaker:</strong> The {@code kafkaPublisher} circuit breaker prevents
 * repeated Kafka attempts when the broker is consistently unavailable.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxProcessor {

    /**
     * Maximum consecutive publish failures before an event is permanently marked FAILED.
     */
    private static final int MAX_RETRY_COUNT = 5;

    /** Repository for reading PENDING events and updating status after publish. */
    private final OutboxEventRepository outboxEventRepository;

    /** Wraps {@code KafkaTemplate.send()} for structured logging and future handling. */
    private final KafkaEventPublisher kafkaEventPublisher;

    /**
     * Polls PENDING outbox events every 500ms and publishes them to Kafka.
     * On success, marks as PUBLISHED. On failure, increments retry count.
     * Events exceeding MAX_RETRY_COUNT are marked FAILED permanently.
     *
     * Circuit breaker: stops polling Kafka when it's consistently failing,
     * preventing the 500ms scheduler from hammering a dead broker.
     */
    @Scheduled(fixedDelayString = "${outbox.processor.poll-interval-ms:5000}")
    @Transactional
    @CircuitBreaker(name = "kafkaPublisher", fallbackMethod = "processOutboxFallback")
    public void processOutbox() {
        List<OutboxEvent> events = outboxEventRepository
                .findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);

        if (events.isEmpty()) {
            return;
        }

        int published = 0;
        List<OutboxEvent> publishedEvents = new ArrayList<>();

        for (OutboxEvent event : events) {
            try {
                kafkaEventPublisher.publish(event.getTopic(), event.getAggregateId(), event.getPayload())
                        .get(); // block to ensure delivery before marking published
                event.markPublished();
                outboxEventRepository.save(event);
                published++;
                publishedEvents.add(event);
                log.info("Outbox → topic={} | eventType={} | aggregateId={}",
                        event.getTopic(), event.getEventType(), event.getAggregateId());
            } catch (Exception e) {
                log.error("Failed to publish outbox event: id={}, topic={}, eventType={}, attempt={}/{}",
                        event.getId(), event.getTopic(), event.getEventType(),
                        event.getRetryCount() + 1, MAX_RETRY_COUNT, e);
                event.markFailed();
                if (event.getRetryCount() >= MAX_RETRY_COUNT) {
                    log.error("Outbox event permanently failed after {} retries: id={}, eventType={}",
                            MAX_RETRY_COUNT, event.getId(), event.getEventType());
                } else {
                    event.retry(); // reset to PENDING for next poll
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
            log.info("Outbox: published {}/{} events [{}]", published, events.size(), breakdown);
        }
    }

    private void processOutboxFallback(CallNotPermittedException e) {
        log.warn("Outbox processing skipped — Kafka circuit breaker is OPEN: {}", e.getMessage());
    }

    private void processOutboxFallback(Exception e) {
        log.error("Outbox processing failed: {}", e.getMessage());
    }
}
