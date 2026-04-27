package com.paymentplatform.orderservice.infrastructure.config;

import com.paymentplatform.commonlib.constants.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares all Kafka topics order-service produces to or consumes from.
 *
 * <p>{@code KafkaAdmin} creates topics at startup with if-not-exists semantics.
 * Replaces the Docker Compose kafka-init shell script for local dev.
 * In production (AWS MSK) topics are managed by Terraform.</p>
 *
 * <p><strong>Topics owned by order-service:</strong></p>
 * <ul>
 *   <li><b>Produces:</b> {@code order.created}, {@code order.completed},
 *       {@code order.cancelled}, {@code order.failed}</li>
 *   <li><b>Consumes:</b> {@code inventory.reserved}, {@code inventory.failed},
 *       {@code payment.completed}, {@code payment.failed}</li>
 * </ul>
 */
@Configuration
public class KafkaTopicConfig {

    // ── Topics this service PRODUCES ──

    /**
     * Order created topic — saga entry point consumed by inventory-service (reserve stock)
     * and notification-service (ORDER_CREATED notification).
     */
    @Bean
    public NewTopic orderCreatedTopic() {
        return TopicBuilder.name(KafkaTopics.ORDER_CREATED)
                .partitions(3).replicas(1).build();
    }

    /** Order completed topic — consumed by notification-service (ORDER_COMPLETED notification). */
    @Bean
    public NewTopic orderCompletedTopic() {
        return TopicBuilder.name(KafkaTopics.ORDER_COMPLETED)
                .partitions(3).replicas(1).build();
    }

    /**
     * Order cancelled topic — consumed by notification-service (ORDER_CANCELLED notification),
     * inventory-service (release reserved stock), and wallet-service (issue refund if applicable).
     */
    @Bean
    public NewTopic orderCancelledTopic() {
        return TopicBuilder.name(KafkaTopics.ORDER_CANCELLED)
                .partitions(3).replicas(1).build();
    }

    /**
     * Order failed topic — consumed by inventory-service (release stock when
     * {@code failedStep=PAYMENT}) and notification-service (ORDER_FAILED notification).
     */
    @Bean
    public NewTopic orderFailedTopic() {
        return TopicBuilder.name(KafkaTopics.ORDER_FAILED)
                .partitions(3).replicas(1).build();
    }

    // ── Topics this service CONSUMES (declared here as safety net) ──

    /** Inventory reserved topic — saga step 2; triggers INVENTORY_RESERVED status update. */
    @Bean
    public NewTopic inventoryReservedTopic() {
        return TopicBuilder.name(KafkaTopics.INVENTORY_RESERVED)
                .partitions(3).replicas(1).build();
    }

    /** Inventory failed topic — triggers saga compensation (order → FAILED). */
    @Bean
    public NewTopic inventoryFailedTopic() {
        return TopicBuilder.name(KafkaTopics.INVENTORY_FAILED)
                .partitions(3).replicas(1).build();
    }

    /** Payment completed topic — triggers order → CONFIRMED status update. */
    @Bean
    public NewTopic paymentCompletedTopic() {
        return TopicBuilder.name(KafkaTopics.PAYMENT_COMPLETED)
                .partitions(3).replicas(1).build();
    }

    /** Payment failed topic — triggers saga compensation (order → FAILED). */
    @Bean
    public NewTopic paymentFailedTopic() {
        return TopicBuilder.name(KafkaTopics.PAYMENT_FAILED)
                .partitions(3).replicas(1).build();
    }
}
