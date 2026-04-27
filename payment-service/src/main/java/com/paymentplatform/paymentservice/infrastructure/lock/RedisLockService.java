package com.paymentplatform.paymentservice.infrastructure.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Redis-based distributed lock implementation using {@code SET NX EX} (set-if-absent with TTL).
 *
 * <p><strong>Why a distributed lock:</strong> payment-service may run as multiple replicas.
 * When Kafka redelivers {@code inventory.reserved} (at-least-once), two instances could race
 * to process the same order. The idempotency key table is the primary safeguard, but it has a
 * TOCTOU window between the check and the charge. The Redis lock closes this window by serialising
 * concurrent processors for the same {@code orderId}.</p>
 *
 * <p><strong>Fencing token:</strong> The lock value is a random UUID generated at acquisition time.
 * {@link #release} validates that the caller's token matches the stored value — preventing an
 * instance from accidentally releasing a lock it no longer holds (e.g., after TTL expiry and
 * re-acquisition by another instance). This is a simplified fencing token pattern.</p>
 *
 * <p><strong>TTL (30 s default):</strong> If the lock holder crashes before releasing, Redis will
 * automatically expire the lock after 30 s. This prevents indefinite lock starvation at the cost
 * of a potential (harmless, idempotency-protected) duplicate attempt after recovery.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RedisLockService {

    /** Spring Redis template for {@code SET NX EX} and {@code GET}/{@code DEL} operations. */
    private final StringRedisTemplate redisTemplate;

    /**
     * Namespace prefix for all payment lock keys. Full key format: {@code "payment:lock:<orderId>"}.
     */
    private static final String LOCK_PREFIX = "payment:lock:";

    /**
     * Default lock TTL. If a lock holder crashes, the lock auto-expires after 30 seconds,
     * allowing another instance to retry. Idempotency prevents the retry from double-charging.
     */
    private static final Duration DEFAULT_TTL = Duration.ofSeconds(30);

    /**
     * Attempts to acquire a lock for the given key.
     *
     * @param key  the lock key (typically orderId)
     * @param ttl  how long the lock lives before auto-expiry
     * @return a lock token if acquired, null if lock is already held
     */
    public String tryAcquire(String key, Duration ttl) {
        String lockKey = LOCK_PREFIX + key;
        String token = UUID.randomUUID().toString();

        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, token, ttl);

        if (Boolean.TRUE.equals(acquired)) {
            log.debug("Lock acquired: key={}, token={}", lockKey, token);
            return token;
        }

        log.debug("Lock not acquired (already held): key={}", lockKey);
        return null;
    }

    /**
     * Convenience overload using default TTL (30s).
     */
    public String tryAcquire(String key) {
        return tryAcquire(key, DEFAULT_TTL);
    }

    /**
     * Releases the lock only if the caller holds the correct token.
     * Prevents accidental release of a lock acquired by another instance
     * after the original lock expired.
     */
    public boolean release(String key, String token) {
        String lockKey = LOCK_PREFIX + key;
        String currentToken = redisTemplate.opsForValue().get(lockKey);

        if (token.equals(currentToken)) {
            redisTemplate.delete(lockKey);
            log.debug("Lock released: key={}", lockKey);
            return true;
        }

        log.warn("Lock release failed — token mismatch: key={}, expected={}, actual={}",
                lockKey, token, currentToken);
        return false;
    }
}
