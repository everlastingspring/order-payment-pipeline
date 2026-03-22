package com.paymentplatform.walletservice.infrastructure.kafka;

import com.paymentplatform.commonlib.constants.KafkaTopics;
import com.paymentplatform.commonlib.events.PaymentCompletedEvent;
import com.paymentplatform.walletservice.application.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Consumes payment.completed events to credit customer wallets.
 *
 * In a real Razorpay-style platform, this would credit the
 * merchant's settlement wallet after successful payment collection.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private final WalletService walletService;

    @KafkaListener(
            topics = KafkaTopics.PAYMENT_COMPLETED,
            groupId = "wallet-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handlePaymentCompleted(PaymentCompletedEvent event, Acknowledgment ack) {
        log.info("Received payment.completed: paymentId={}, orderId={}, amount={} {}",
                event.getPaymentId(), event.getOrderId(),
                event.getAmount().getAmount(), event.getAmount().getCurrency());

        try {
            walletService.creditFromPayment(event);
            ack.acknowledge();
            log.info("payment.completed processed and acknowledged: paymentId={}", event.getPaymentId());
        } catch (Exception e) {
            log.error("Failed to process payment.completed: paymentId={}, error={}",
                    event.getPaymentId(), e.getMessage(), e);
            throw e;
        }
    }
}
