package com.paymentplatform.inventoryservice.infrastructure.config;

import com.paymentplatform.commonlib.constants.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares Kafka topics for inventory-service using Spring Kafka's {@code KafkaAdmin}.
 *
 * <p>Topics are created at startup with if-not-exists semantics.
 * In production (AWS MSK) topics are managed by Terraform.</p>
 *
 * <p><strong>Topics owned by inventory-service:</strong></p>
 * <ul>
 *   <li><b>Produces:</b> {@code inventory.reserved}, {@code inventory.failed}</li>
 *   <li><b>Consumes:</b> {@code order.created}, {@code order.cancelled}, {@code order.failed}</li>
 * </ul>
 */
@Configuration
public class KafkaTopicConfig {

    // ── Topics this service PRODUCES ──

    /**
     * Inventory reserved topic — published after the two-pass reservation succeeds.
     * Consumed by order-service (→ INVENTORY_RESERVED) and payment-service (saga trigger).
     */
    @Bean
    public NewTopic inventoryReservedTopic() {
        return TopicBuilder.name(KafkaTopics.INVENTORY_RESERVED)
                .partitions(3).replicas(1).build();
    }

    /**
     * Inventory failed topic — published when stock reservation fails (insufficient stock).
     * Consumed by order-service (saga compensation → order → FAILED).
     */
    @Bean
    public NewTopic inventoryFailedTopic() {
        return TopicBuilder.name(KafkaTopics.INVENTORY_FAILED)
                .partitions(3).replicas(1).build();
    }

    // ── Topics this service CONSUMES ──

    /** Order created topic — triggers two-pass stock reservation for all order line items. */
    @Bean
    public NewTopic orderCreatedTopic() {
        return TopicBuilder.name(KafkaTopics.ORDER_CREATED)
                .partitions(3).replicas(1).build();
    }

    /** Order cancelled topic — triggers stock release for all ACTIVE reservations on the order. */
    @Bean
    public NewTopic orderCancelledTopic() {
        return TopicBuilder.name(KafkaTopics.ORDER_CANCELLED)
                .partitions(3).replicas(1).build();
    }

    /**
     * Order failed topic — triggers stock release, but only when {@code failedStep=PAYMENT}
     * (stock was already reserved before the payment step failed).
     */
    @Bean
    public NewTopic orderFailedTopic() {
        return TopicBuilder.name(KafkaTopics.ORDER_FAILED)
                .partitions(3).replicas(1).build();
    }
}
