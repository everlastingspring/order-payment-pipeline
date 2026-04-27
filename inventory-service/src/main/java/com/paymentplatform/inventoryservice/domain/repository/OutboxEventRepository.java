package com.paymentplatform.inventoryservice.domain.repository;

import com.paymentplatform.inventoryservice.domain.entity.OutboxEvent;
import com.paymentplatform.inventoryservice.domain.entity.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link OutboxEvent} entities in inventory-service.
 *
 * <p>The outbox table is the durable buffer between DB writes and Kafka publishes.
 * {@code OutboxProcessor} is the sole reader; {@code InventoryService} is the sole writer.</p>
 */
@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Fetches the next batch of up to 50 {@link OutboxEvent}s in the given status,
     * ordered oldest-first to preserve per-order event ordering across the saga.
     *
     * @param status the status to filter on (typically {@link OutboxStatus#PENDING})
     * @return list of at most 50 events ordered by {@code createdAt ASC}
     */
    List<OutboxEvent> findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus status);
}
