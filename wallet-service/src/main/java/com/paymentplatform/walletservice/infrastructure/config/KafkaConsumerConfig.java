package com.paymentplatform.walletservice.infrastructure.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka consumer infrastructure configuration for wallet-service.
 *
 * <p>Consumer group ID is {@code "wallet-service-group"}. Wallet-service consumes
 * {@code payment.completed} (wallet debit confirmation) and {@code order.cancelled}
 * (wallet refund when order had been paid). Both operations write to the
 * append-only ledger and are idempotency-guarded at the service layer.</p>
 *
 * <p>Uses {@code AckMode.MANUAL} with {@code enable.auto.commit=false} for at-least-once
 * delivery semantics. Observation is enabled for distributed tracing via OpenTelemetry.</p>
 */
@Configuration
public class KafkaConsumerConfig {

    /** Kafka bootstrap servers from {@code spring.kafka.bootstrap-servers} in application.yml. */
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * Creates the consumer factory for the {@code wallet-service-group} consumer group.
     *
     * @return configured {@link ConsumerFactory} with string deserializers and manual commit
     */
    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "wallet-service-group");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * Creates the listener container factory with manual ack mode and OTel observation.
     *
     * @return configured {@link ConcurrentKafkaListenerContainerFactory}
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(3);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.getContainerProperties().setObservationEnabled(true);
        return factory;
    }
}
