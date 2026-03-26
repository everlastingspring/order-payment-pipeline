package com.paymentplatform.paymentservice.application.processor;

import com.paymentplatform.commonlib.enums.PaymentMethod;
import com.paymentplatform.commonlib.exception.InsufficientFundsException;
import com.paymentplatform.paymentservice.infrastructure.client.WalletClient;
import com.paymentplatform.paymentservice.infrastructure.gateway.GatewayResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Processes payments using the customer's internal wallet balance.
 *
 * Flow:
 *   1. Calls wallet-service REST to debit the customer's balance
 *   2. If successful  → returns GatewayResponse.success with a WALLET-xxx txn reference
 *   3. If insufficient balance → returns GatewayResponse.failure (no exception thrown —
 *      payment.failed saga compensation handles the order cancellation)
 *   4. If wallet-service is down → throws RuntimeException (Resilience4j circuit breaker catches it)
 *
 * Adding UPI later: create UpiPaymentProcessor, supports(UPI), call UPI gateway API.
 * Zero changes needed in PaymentService or here.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WalletPaymentProcessor implements PaymentProcessor {

    private final WalletClient walletClient;

    @Override
    public boolean supports(PaymentMethod method) {
        return method == PaymentMethod.WALLET;
    }

    @Override
    public GatewayResponse charge(String orderId, String customerId, BigDecimal amount, String currency) {
        try {
            walletClient.debit(customerId, amount, currency, orderId);

            String txnRef = "WALLET-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
            log.info("Wallet payment successful: orderId={}, customerId={}, amount={} {}, txnRef={}",
                    orderId, customerId, amount, currency, txnRef);

            return GatewayResponse.success(txnRef);

        } catch (InsufficientFundsException e) {
            log.warn("Wallet payment declined — insufficient funds: orderId={}, customerId={}, requested={}, available={}",
                    orderId, customerId, e.getRequested(), e.getAvailable());
            return GatewayResponse.failure("INSUFFICIENT_WALLET_BALANCE",
                    "Wallet balance insufficient: requested " + amount + " " + currency);

        } catch (Exception e) {
            // Wallet service unavailable — propagate so circuit breaker can act
            log.error("Wallet service call failed: orderId={}, error={}", orderId, e.getMessage());
            throw new RuntimeException("Wallet payment failed for orderId=" + orderId, e);
        }
    }
}
