package com.paymentplatform.orderservice.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentplatform.commonlib.constants.KafkaTopics;
import com.paymentplatform.commonlib.events.InventoryFailedEvent;
import com.paymentplatform.commonlib.events.InventoryReservedEvent;
import com.paymentplatform.orderservice.application.saga.OrderSagaManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for inventory lifecycle events that advance or fail the order saga.
 *
 * <p><strong>Topics consumed:</strong></p>
 * <ul>
 *   <li>{@code inventory.reserved} → {@link OrderSagaManager#handleInventoryReserved} —
 *       transitions order to {@code INVENTORY_RESERVED}; payment-service will now proceed.</li>
 *   <li>{@code inventory.failed} → {@link OrderSagaManager#handleInventoryFailed} —
 *       transitions order to {@code FAILED}; publishes {@code order.failed} (failedStep=INVENTORY).</li>
 * </ul>
 *
 * <p>Note: order-service and payment-service both consume {@code inventory.reserved} from
 * different consumer groups — order-service updates the status, payment-service triggers the charge.
 * Kafka fan-out ensures both receive the event independently.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryEventConsumer {

    /** Saga state machine that handles the actual order status transitions. */
    private final OrderSagaManager sagaManager;

    /** Deserialises raw Kafka JSON into typed event objects. */
    private final ObjectMapper objectMapper;

    /**
     * Handles {@code inventory.reserved} — advances the order saga to the payment step.
     *
     * @param payload raw JSON string from Kafka
     * @param ack     manual acknowledgment handle; ack'd only on success
     */
    @KafkaListener(
            topics = KafkaTopics.INVENTORY_RESERVED,
            groupId = "order-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onInventoryReserved(String payload, Acknowledgment ack) {
        try {
            InventoryReservedEvent event = objectMapper.readValue(payload, InventoryReservedEvent.class);
            log.info("Received inventory.reserved: orderId={}, reservationId={}",
                    event.getOrderId(), event.getReservationId());
            sagaManager.handleInventoryReserved(event);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error processing inventory.reserved: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Handles {@code inventory.failed} — terminates the saga with FAILED status.
     * No payment was attempted; no stock was reserved.
     *
     * @param payload raw JSON string from Kafka
     * @param ack     manual acknowledgment handle
     */
    @KafkaListener(
            topics = KafkaTopics.INVENTORY_FAILED,
            groupId = "order-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onInventoryFailed(String payload, Acknowledgment ack) {
        try {
            InventoryFailedEvent event = objectMapper.readValue(payload, InventoryFailedEvent.class);
            log.info("Received inventory.failed: orderId={}, reason={}",
                    event.getOrderId(), event.getFailureReason());
            sagaManager.handleInventoryFailed(event);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Error processing inventory.failed: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}
