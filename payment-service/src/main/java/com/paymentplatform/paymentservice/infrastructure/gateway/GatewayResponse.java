package com.paymentplatform.paymentservice.infrastructure.gateway;

/**
 * Immutable value object returned by every {@link PaymentProcessor} implementation.
 *
 * <p>Extracted from {@code PaymentGatewaySimulator} so that all processors ({@code WalletPaymentProcessor},
 * {@code GatewayPaymentProcessor}, and future ones) return a uniform result type that
 * {@code PaymentService.executePayment()} can handle without processor-specific branching.</p>
 *
 * <p><strong>Usage patterns:</strong></p>
 * <pre>
 *   // Successful charge
 *   GatewayResponse.success("WALLET-ABCD1234EF56")
 *
 *   // Declined charge
 *   GatewayResponse.failure("INSUFFICIENT_WALLET_BALANCE", "Wallet balance insufficient: ...")
 *
 *   // Gateway down (circuit breaker opened)
 *   GatewayResponse.failure("GATEWAY_UNAVAILABLE", "Payment gateway is temporarily unavailable")
 * </pre>
 */
public record GatewayResponse(
        /** Whether the charge succeeded. When {@code true}, {@link #transactionReference} is set. */
        boolean successful,

        /**
         * Unique reference from the payment provider for a successful charge.
         * Format varies by processor: {@code "WALLET-<12-char-UUID>"} for wallet,
         * {@code "TXN-<12-char-UUID>"} for the gateway simulator.
         * {@code null} when {@link #successful} is {@code false}.
         */
        String transactionReference,

        /**
         * Short machine-readable error code when the charge failed.
         * Examples: {@code "INSUFFICIENT_WALLET_BALANCE"}, {@code "GATEWAY_UNAVAILABLE"}, {@code "INSUFFICIENT_FUNDS"}.
         * {@code null} when {@link #successful} is {@code true}.
         */
        String failureCode,

        /**
         * Human-readable failure description displayed in {@code payment.failed} events and logs.
         * {@code null} when {@link #successful} is {@code true}.
         */
        String failureReason
) {
    /**
     * Creates a successful response.
     *
     * @param transactionReference provider-assigned transaction ID; must not be null
     * @return {@code GatewayResponse} with {@code successful=true}
     */
    public static GatewayResponse success(String transactionReference) {
        return new GatewayResponse(true, transactionReference, null, null);
    }

    /**
     * Creates a failed response.
     *
     * @param failureCode   short error code (e.g. {@code "INSUFFICIENT_FUNDS"})
     * @param failureReason human-readable description
     * @return {@code GatewayResponse} with {@code successful=false}
     */
    public static GatewayResponse failure(String failureCode, String failureReason) {
        return new GatewayResponse(false, null, failureCode, failureReason);
    }
}
