package com.paymentplatform.commonlib.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request sent by payment-service to wallet-service to debit customer wallet.
 * Shared in common-lib because payment-service (sender) and
 * wallet-service (receiver) both reference it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletDebitRequest {

    /** Customer whose wallet to debit. */
    private String customerId;

    /** Amount to debit. */
    private BigDecimal amount;

    /** Currency (e.g. "INR"). */
    private String currency;

    /** Order being paid for — stored as ledger referenceId. */
    private String orderId;
}
