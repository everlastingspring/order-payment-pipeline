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

import java.util.List;

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
        for (OutboxEvent event : pendingEvents) {
            try {
                kafkaEventPublisher.publish(event.getTopic(), event.getAggregateId(), event.getPayload())
                        .get();

                event.markPublished();
                outboxEventRepository.save(event);
                published++;

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

        if (published > 0) {
            log.info("Outbox: published {}/{} events", published, pendingEvents.size());
        }
    }

    private void processOutboxFallback(CallNotPermittedException e) {
        log.warn("Outbox processing skipped — Kafka circuit breaker is OPEN: {}", e.getMessage());
    }

    private void processOutboxFallback(Exception e) {
        log.error("Outbox processing failed: {}", e.getMessage());
    }
}
