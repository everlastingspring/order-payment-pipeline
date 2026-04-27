package com.paymentplatform.orderservice.infrastructure.kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka consumer infrastructure configuration for order-service.
 *
 * <p><strong>Key settings and their rationale:</strong></p>
 * <ul>
 *   <li>{@code auto.offset.reset=earliest} — on first start (or after a group reset) the service
 *       processes all available events from the beginning of the topic rather than skipping them.</li>
 *   <li>{@code enable.auto.commit=false} — offsets are committed manually via
 *       {@code Acknowledgment.acknowledge()} in each listener, ensuring at-least-once delivery.
 *       An event is never marked as consumed until the service has successfully persisted it.</li>
 *   <li>{@code AckMode.MANUAL} — matches the manual-commit approach; the container does not
 *       auto-commit after the listener returns, giving listeners full control.</li>
 *   <li>{@code concurrency=3} — three concurrent listener threads to handle parallel partitions
 *       without over-provisioning connections.</li>
 *   <li>{@code observationEnabled=true} — integrates with Micrometer/OpenTelemetry to propagate
 *       trace context through Kafka message headers.</li>
 * </ul>
 *
 * <p>All services that consume Kafka events use the same pattern; this config is local to
 * order-service and does not affect other consumers.</p>
 */
@Configuration
public class KafkaConsumerConfig {

    /** Kafka bootstrap servers from {@code spring.kafka.bootstrap-servers} in application.yml. */
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * Creates the consumer factory with string key and value deserializers.
     * Payload is raw JSON; each listener deserializes into domain events using {@code ObjectMapper}.
     *
     * @return configured {@link ConsumerFactory} used by the listener container factory
     */
    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * Creates the listener container factory that wraps all {@code @KafkaListener} methods.
     * Configures manual ack mode, OTel observation, and concurrency.
     *
     * @return configured {@link ConcurrentKafkaListenerContainerFactory} registered as the
     *         default factory for all listeners in this service
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.getContainerProperties().setObservationEnabled(true);
        factory.setConcurrency(3);
        return factory;
    }
}
