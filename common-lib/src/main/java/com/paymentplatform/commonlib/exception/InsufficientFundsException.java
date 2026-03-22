package com.paymentplatform.commonlib.exception;

import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class InsufficientFundsException extends RuntimeException {

    private final String walletId;
    private final BigDecimal requested;
    private final BigDecimal available;

    public InsufficientFundsException(String walletId, BigDecimal requested, BigDecimal available) {
        super("Insufficient funds in wallet " + walletId + ": requested=" + requested + ", available=" + available);
        this.walletId = walletId;
        this.requested = requested;
        this.available = available;
    }
}
