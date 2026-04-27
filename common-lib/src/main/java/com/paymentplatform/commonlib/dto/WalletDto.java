package com.paymentplatform.commonlib.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * API transfer object representing a customer's wallet.
 *
 * <p>Returned by {@code GET /api/wallets/{walletId}}, {@code GET /api/wallets/customer/{customerId}},
 * and write operations (top-up, debit). Includes the current balance projection and a list of
 * recent ledger entries for UI display.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WalletDto {

    /** UUID of the wallet. */
    private String walletId;

    /** Customer who owns this wallet (one wallet per customer). */
    private String customerId;

    /** Current balance — a cached projection over all ledger entries. */
    private MoneyDto balance;

    /**
     * Recent ledger entries for this wallet, ordered newest first.
     * Populated when loading the wallet aggregate with its entries initialised.
     * May be null or empty in lightweight balance-check responses.
     */
    private List<LedgerEntryDto> recentTransactions;

    /** When the wallet was created. */
    private Instant createdAt;

    /** When the wallet balance was last updated. */
    private Instant updatedAt;
}
