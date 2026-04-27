package com.paymentplatform.walletservice.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentplatform.commonlib.constants.KafkaTopics;
import com.paymentplatform.commonlib.events.OrderCancelledEvent;
import com.paymentplatform.walletservice.application.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for order cancellation events that require wallet refunds.
 *
 * <p><strong>Topic consumed:</strong> {@code order.cancelled}</p>
 *
 * <p>A refund is issued only when {@link com.paymentplatform.commonlib.events.OrderCancelledEvent#getRefundAmount()}
 * is non-null and greater than zero. This indicates that a payment was completed before the
 * cancellation occurred.</p>
 *
 * <p><strong>Cancellation scenarios:</strong></p>
 * <ul>
 *   <li>Cancelled at {@code PENDING} — no payment yet, {@code refundAmount} is null → no refund</li>
 *   <li>Cancelled at {@code INVENTORY_RESERVED} — payment not yet processed, {@code refundAmount} is null → no refund</li>
 *   <li>Cancelled at {@code CONFIRMED} (payment done) — {@code refundAmount = orderTotal} → wallet REFUND ledger entry</li>
 * </ul>
 *
 * <p>The refund operation in {@code WalletService.refundForCancellation()} is idempotent:
 * duplicate {@code order.cancelled} deliveries are detected by the ledger's unique constraint
 * on {@code (wallet_id, reference_id, transaction_type)}.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    /** Handles the actual refund logic, including idempotency and balance update. */
    private final WalletService walletService;

    /** Deserialises the raw Kafka JSON payload into typed event objects. */
    private final ObjectMapper objectMapper;

    /**
     * Handles {@code order.cancelled} — issues a wallet refund if the order was paid before cancellation.
     * Delegates to {@code WalletService.refundForCancellation()} which is a no-op when {@code refundAmount ≤ 0}.
     *
     * @param payload raw JSON string from Kafka
     * @param ack     manual acknowledgment handle; ack'd only on success
     */
    @KafkaListener(
            topics = KafkaTopics.ORDER_CANCELLED,
            groupId = "wallet-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleOrderCancelled(String payload, Acknowledgment ack) {
        try {
            OrderCancelledEvent event = objectMapper.readValue(payload, OrderCancelledEvent.class);
            log.info("Received order.cancelled: orderId={}, customerId={}, refundAmount={}",
                    event.getOrderId(), event.getCustomerId(), event.getRefundAmount());

            walletService.refundForCancellation(
                    event.getCustomerId(),
                    event.getRefundAmount(),
                    event.getOrderId()
            );

            ack.acknowledge();
            log.info("order.cancelled processed: orderId={}", event.getOrderId());

        } catch (Exception e) {
            log.error("Failed to process order.cancelled: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}
