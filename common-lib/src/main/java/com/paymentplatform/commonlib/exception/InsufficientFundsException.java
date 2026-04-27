package com.paymentplatform.commonlib.exception;

import lombok.Getter;

import java.math.BigDecimal;

/**
 * Thrown by {@code Wallet.debit()} when the wallet balance is too low.
 *
 * <p>Caught by {@code WalletPaymentProcessor} and converted to a
 * {@code GatewayResponse.failure("INSUFFICIENT_WALLET_BALANCE")} so the
 * payment saga can gracefully fail without a stack trace propagating to Kafka.</p>
 *
 * <p>Also caught by {@code GlobalExceptionHandler} in wallet-service
 * and translated to a 400 Bad Request response for direct REST callers.</p>
 */
@Getter
public class InsufficientFundsException extends RuntimeException {

    /** The wallet that lacked funds — used for logging context. */
    private final String walletId;

    /** Amount that was requested to be debited. */
    private final BigDecimal requested;

    /** Actual available balance at the time of the debit attempt. */
    private final BigDecimal available;

    public InsufficientFundsException(String walletId, BigDecimal requested, BigDecimal available) {
        super("Insufficient funds in wallet " + walletId + ": requested=" + requested + ", available=" + available);
        this.walletId = walletId;
        this.requested = requested;
        this.available = available;
    }
}
