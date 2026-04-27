package com.paymentplatform.paymentservice.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis infrastructure configuration for payment-service.
 *
 * <p>Redis is used in payment-service for distributed locking via {@code RedisLockService}.
 * The lock prevents concurrent payment attempts for the same order from racing past the
 * idempotency check and charging the customer twice.</p>
 *
 * <p>A single {@link StringRedisTemplate} bean is sufficient — payment-service only
 * stores and reads string keys (lock tokens). No complex data structures are required.</p>
 */
@Configuration
public class RedisConfig {

    /**
     * Creates a {@link StringRedisTemplate} backed by the auto-configured Redis connection factory.
     * Uses Spring Boot's {@code spring.data.redis.*} properties for host, port, and password.
     *
     * @param connectionFactory the auto-configured Redis connection factory
     * @return string-typed Redis template used by {@code RedisLockService}
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
