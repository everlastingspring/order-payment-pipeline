package com.paymentplatform.walletservice.infrastructure.kafka;

/**
 * Intentionally empty.
 *
 * Wallet-service no longer consumes payment.completed from Kafka.
 *
 * Reason: wallet debit now happens SYNCHRONOUSLY during payment processing —
 * payment-service calls POST /api/wallets/debit via REST before publishing
 * payment.completed. By the time payment.completed is on the bus, the wallet
 * is already updated. No double-processing needed here.
 *
 * If refund-on-cancellation is needed in future, add an OrderEventConsumer
 * that listens to order.cancelled and calls walletService.refund().
 */
public class PaymentEventConsumer {
    // No-op — kept as a marker so the git history explains the removal.
}
