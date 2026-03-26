package com.paymentplatform.paymentservice.application.processor;

import com.paymentplatform.commonlib.enums.PaymentMethod;
import com.paymentplatform.paymentservice.infrastructure.gateway.GatewayResponse;
import com.paymentplatform.paymentservice.infrastructure.gateway.PaymentGatewaySimulator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Processes payments via external payment gateway (UPI, CARD, NET_BANKING, EMI).
 *
 * Currently wraps PaymentGatewaySimulator which always succeeds for non-.99 amounts.
 * In production, this would be replaced with actual Razorpay/Stripe SDK calls.
 *
 * This processor is intentionally NOT supporting any method right now.
 * Enable it per-method as external gateway integrations are added.
 * Example: when UPI goes live, add PaymentMethod.UPI to the supports() check.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GatewayPaymentProcessor implements PaymentProcessor {

    private final PaymentGatewaySimulator simulator;

    @Override
    public boolean supports(PaymentMethod method) {
        // Uncomment each line as the real gateway integration is built:
        // return method == PaymentMethod.UPI
        //     || method == PaymentMethod.CARD
        //     || method == PaymentMethod.NET_BANKING
        //     || method == PaymentMethod.EMI;
        return false; // no external gateway yet
    }

    @Override
    public GatewayResponse charge(String orderId, String customerId, BigDecimal amount, String currency) {
        log.info("External gateway charge: orderId={}, method=GATEWAY, amount={} {}", orderId, amount, currency);
        // PaymentMethod not needed for simulator — it ignores it
        return simulator.charge(orderId, amount, currency, null);
    }
}
