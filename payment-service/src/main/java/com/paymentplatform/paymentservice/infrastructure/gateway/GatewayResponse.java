package com.paymentplatform.paymentservice.infrastructure.gateway;

/**
 * Immutable result returned by any PaymentProcessor implementation.
 * Extracted from PaymentGatewaySimulator so all processors share the same result type.
 */
public record GatewayResponse(
        boolean successful,
        String transactionReference,
        String failureCode,
        String failureReason
) {
    public static GatewayResponse success(String transactionReference) {
        return new GatewayResponse(true, transactionReference, null, null);
    }

    public static GatewayResponse failure(String failureCode, String failureReason) {
        return new GatewayResponse(false, null, failureCode, failureReason);
    }
}
