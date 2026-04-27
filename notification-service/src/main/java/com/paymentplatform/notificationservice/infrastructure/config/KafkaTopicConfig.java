package com.paymentplatform.notificationservice.infrastructure.config;

import com.paymentplatform.commonlib.constants.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares Kafka topics for notification-service using Spring Kafka's {@code KafkaAdmin}.
 *
 * <p>Topics are created at startup with if-not-exists semantics.
 * In production (AWS MSK) topics are managed by Terraform.</p>
 *
 * <p><strong>Topics owned by notification-service:</strong></p>
 * <ul>
 *   <li><b>Produces:</b> {@code notification.dlq} (1 partition — low throughput, ordered)</li>
 *   <li><b>Consumes:</b> {@code order.created}, {@code order.completed}, {@code order.cancelled},
 *       {@code order.failed}, {@code payment.completed}, {@code payment.failed}</li>
 * </ul>
 *
 * <p>Notification-service is a pure consumer for most topics — it does not produce business
 * events, only DLQ records for failed notification attempts.</p>
 */
@Configuration
public class KafkaTopicConfig {

    // ── Topics this service PRODUCES ──

    /**
     * Notification DLQ topic — published when a notification exhausts all send attempts.
     * Uses 1 partition (low volume; ordering within the DLQ is a useful property).
     */
    @Bean
    public NewTopic notificationDlqTopic() {
        return TopicBuilder.name(KafkaTopics.NOTIFICATION_DLQ)
                .partitions(1).replicas(1).build();
    }

    // ── Topics this service CONSUMES ──

    @Bean
    public NewTopic orderCreatedTopic() {
        return TopicBuilder.name(KafkaTopics.ORDER_CREATED)
                .partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic orderCompletedTopic() {
        return TopicBuilder.name(KafkaTopics.ORDER_COMPLETED)
                .partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic orderCancelledTopic() {
        return TopicBuilder.name(KafkaTopics.ORDER_CANCELLED)
                .partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic orderFailedTopic() {
        return TopicBuilder.name(KafkaTopics.ORDER_FAILED)
                .partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic paymentCompletedTopic() {
        return TopicBuilder.name(KafkaTopics.PAYMENT_COMPLETED)
                .partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic paymentFailedTopic() {
        return TopicBuilder.name(KafkaTopics.PAYMENT_FAILED)
                .partitions(3).replicas(1).build();
    }
}
