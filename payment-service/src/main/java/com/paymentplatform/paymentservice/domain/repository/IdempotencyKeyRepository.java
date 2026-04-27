package com.paymentplatform.paymentservice.domain.repository;

import com.paymentplatform.paymentservice.domain.entity.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link IdempotencyKey} entities.
 *
 * <p>Idempotency keys are stored in the DB to guarantee at-most-once payment processing.
 * A Redis distributed lock is acquired first (fast-path check); this repository is the
 * durable backstop that persists the consumed-key record across service restarts.</p>
 *
 * <p>Keys have a configurable TTL ({@code payment.idempotency.ttl-seconds}); expired rows
 * should be purged periodically via {@link #deleteExpiredKeys(Instant)} to prevent
 * unbounded table growth.</p>
 */
@Repository
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, UUID> {

    /**
     * Looks up an idempotency key record by the key string provided in the request header.
     *
     * @param key the client-supplied idempotency key value
     * @return the key record if already consumed, or empty if this is a new request
     */
    Optional<IdempotencyKey> findByKey(String key);

    /**
     * Fast existence check used before attempting to acquire the Redis lock.
     *
     * @param key the idempotency key to check
     * @return {@code true} if the key has already been consumed
     */
    boolean existsByKey(String key);

    /**
     * Bulk-deletes all {@link IdempotencyKey} rows whose {@code expiresAt} timestamp
     * is earlier than the given instant. Intended to be called by a scheduled cleanup job.
     *
     * @param now the current instant; rows with {@code expiresAt < now} are deleted
     * @return the number of rows deleted
     */
    @Modifying
    @Query("DELETE FROM IdempotencyKey ik WHERE ik.expiresAt < :now")
    int deleteExpiredKeys(Instant now);
}
