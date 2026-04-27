package com.paymentplatform.walletservice.infrastructure.config;

import com.paymentplatform.commonlib.constants.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares Kafka topics for wallet-service using Spring Kafka's {@code KafkaAdmin}.
 *
 * <p>Wallet-service is a pure consumer — it produces no Kafka events of its own
 * (wallet debits are performed via synchronous REST called by payment-service;
 * refunds are triggered by consuming {@code order.cancelled}).</p>
 *
 * <p><strong>Topics consumed by wallet-service:</strong></p>
 * <ul>
 *   <li>{@code payment.completed} — triggers a DEBIT ledger entry (wallet debit confirmation)</li>
 *   <li>{@code order.cancelled} — triggers a REFUND ledger entry when {@code refundAmount > 0}</li>
 * </ul>
 */
@Configuration
public class KafkaTopicConfig {

    // ── Topics this service CONSUMES ──

    /**
     * Payment completed topic — triggers a DEBIT ledger entry for the completed payment amount.
     */
    @Bean
    public NewTopic paymentCompletedTopic() {
        return TopicBuilder.name(KafkaTopics.PAYMENT_COMPLETED)
                .partitions(3).replicas(1).build();
    }

    /**
     * Order cancelled topic — triggers a REFUND ledger entry when
     * {@code OrderCancelledEvent.refundAmount.amount > 0} (i.e. CONFIRMED order cancellation).
     */
    @Bean
    public NewTopic orderCancelledTopic() {
        return TopicBuilder.name(KafkaTopics.ORDER_CANCELLED)
                .partitions(3).replicas(1).build();
    }
}
