package com.paymentplatform.paymentservice.infrastructure.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Distributed lock using Redis SET NX EX.
 *
 * Prevents concurrent payment processing for the same order
 * across multiple service instances. The lock value is a unique
 * token so only the holder can release it (no accidental unlock
 * by another thread whose lock expired).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RedisLockService {

    private final StringRedisTemplate redisTemplate;

    private static final String LOCK_PREFIX = "payment:lock:";
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
