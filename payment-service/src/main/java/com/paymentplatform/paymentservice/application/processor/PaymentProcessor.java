package com.paymentplatform.paymentservice.application.processor;

import com.paymentplatform.commonlib.enums.PaymentMethod;
import com.paymentplatform.paymentservice.infrastructure.gateway.GatewayResponse;

import java.math.BigDecimal;

/**
 * Strategy interface for payment processing.
 *
 * Each payment method (WALLET, UPI, CARD, etc.) gets its own implementation.
 * PaymentService discovers all implementations via Spring's dependency injection
 * and routes to the correct one based on the order's paymentMethod.
 *
 * To add a new payment method (e.g. UPI):
 *   1. Create UpiPaymentProcessor implements PaymentProcessor
 *   2. Annotate with @Component
 *   3. Return true from supports(UPI)
 *   No changes needed in PaymentService.
 */
public interface PaymentProcessor {

    /**
     * Returns true if this processor handles the given payment method.
     */
    boolean supports(PaymentMethod method);

    /**
     * Attempts to charge the customer.
     *
     * @param orderId    order being paid for
     * @param customerId customer being charged
     * @param amount     amount to charge
     * @param currency   currency (e.g. "INR")
     * @return GatewayResponse indicating success or failure
     */
    GatewayResponse charge(String orderId, String customerId, BigDecimal amount, String currency);
}
