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
 * Consumes order.cancelled events to refund customer wallets.
 *
 * Refund only happens when OrderCancelledEvent.refundAmount > 0,
 * meaning the payment was already completed before the cancellation.
 *
 * Examples:
 *  - Order cancelled at PENDING status → refundAmount is null → no refund
 *  - Order cancelled at COMPLETED status → refundAmount = order total → wallet credited
 *  - Order cancelled at INVENTORY_RESERVED (before payment) → refundAmount = null → no refund
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final WalletService walletService;
    private final ObjectMapper objectMapper;

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
