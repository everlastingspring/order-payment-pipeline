package com.paymentplatform.orderservice.domain.repository;

import com.paymentplatform.orderservice.domain.entity.OutboxEvent;
import com.paymentplatform.orderservice.domain.entity.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link OutboxEvent} entities in order-service.
 *
 * <p>The outbox table is the durable buffer between DB writes and Kafka publishes.
 * {@code OutboxProcessor} is the sole reader of this repository during its poll cycle;
 * {@code OrderService} and {@code OrderSagaManager} are the sole writers (via {@code save()}).</p>
 */
@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Fetches the next batch of up to 50 {@link OutboxEvent}s in the given status,
     * ordered oldest-first to preserve per-aggregate event ordering.
     *
     * <p>Called by {@code OutboxProcessor} each poll cycle. The {@code TOP 50} cap
     * prevents a single cycle from holding a long-running transaction while also
     * ensuring steady forward progress if events are produced faster than they are consumed.</p>
     *
     * @param status the status to filter on (typically {@link OutboxStatus#PENDING})
     * @return list of at most 50 events ordered by {@code createdAt ASC}
     */
    List<OutboxEvent> findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus status);
}
