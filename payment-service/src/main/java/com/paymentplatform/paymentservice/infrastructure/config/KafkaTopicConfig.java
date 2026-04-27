package com.paymentplatform.paymentservice.infrastructure.config;

import com.paymentplatform.commonlib.constants.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares Kafka topics for payment-service using Spring Kafka's {@code KafkaAdmin}.
 *
 * <p>Topics are created at startup with if-not-exists semantics — safe to re-run.
 * In local dev this replaces the Kafka initialisation script; in production (AWS MSK)
 * topics are managed by Terraform.</p>
 *
 * <p><strong>Topics owned by this service:</strong></p>
 * <ul>
 *   <li><b>Produces:</b> {@code payment.completed}, {@code payment.failed}</li>
 *   <li><b>Consumes:</b> {@code inventory.reserved} (saga trigger)</li>
 * </ul>
 *
 * <p>All topics use 3 partitions for parallel consumption and 1 replica for local dev
 * (production Terraform sets replication factor ≥ 2).</p>
 */
@Configuration
public class KafkaTopicConfig {

    // ── Topics this service PRODUCES ──

    /**
     * Payment completed topic — consumed by order-service (→ CONFIRMED),
     * notification-service, and wallet-service.
     */
    @Bean
    public NewTopic paymentCompletedTopic() {
        return TopicBuilder.name(KafkaTopics.PAYMENT_COMPLETED)
                .partitions(3).replicas(1).build();
    }

    /**
     * Payment failed topic — consumed by order-service (saga compensation → order.failed).
     */
    @Bean
    public NewTopic paymentFailedTopic() {
        return TopicBuilder.name(KafkaTopics.PAYMENT_FAILED)
                .partitions(3).replicas(1).build();
    }

    // ── Topics this service CONSUMES ──

    /**
     * Inventory reserved topic — the sequential saga trigger. Payment-service starts
     * processing only after inventory-service has confirmed stock is locked.
     */
    @Bean
    public NewTopic inventoryReservedTopic() {
        return TopicBuilder.name(KafkaTopics.INVENTORY_RESERVED)
                .partitions(3).replicas(1).build();
    }
}
