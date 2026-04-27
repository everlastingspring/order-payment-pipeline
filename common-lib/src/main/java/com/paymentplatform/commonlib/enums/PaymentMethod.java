package com.paymentplatform.commonlib.enums;

/**
 * Payment methods supported by the platform.
 *
 * <p>Currently only {@code WALLET} is live.
 * {@code WalletPaymentProcessor} handles it via a synchronous REST call to wallet-service.
 * All other methods are declared for future integrations via {@code GatewayPaymentProcessor}
 * but {@code GatewayPaymentProcessor.supports()} returns {@code false} for all of them.</p>
 */
public enum PaymentMethod {

    /** Unified Payments Interface — future external gateway integration. */
    UPI,

    /** Debit or credit card — future external gateway integration. */
    CARD,

    /** Bank net banking — future external gateway integration. */
    NET_BANKING,

    /**
     * Internal wallet balance.
     * Currently the only active payment method.
     * Handled by {@code WalletPaymentProcessor} via REST to wallet-service.
     */
    WALLET,

    /** Equated Monthly Instalments — future external gateway integration. */
    EMI
}
