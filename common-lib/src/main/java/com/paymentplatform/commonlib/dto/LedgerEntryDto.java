package com.paymentplatform.commonlib.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.paymentplatform.commonlib.enums.WalletTransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * API transfer object representing a single wallet ledger entry.
 *
 * <p>Returned as part of {@link WalletDto#getRecentTransactions()} and by the paginated
 * ledger endpoint {@code GET /api/wallets/{walletId}/ledger}.</p>
 *
 * <p>Each entry represents one atomic balance change (CREDIT, DEBIT, or REFUND) and is
 * immutable — entries are append-only in the underlying event-sourced model.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LedgerEntryDto {

    /** UUID of the ledger entry (mapped from {@code LedgerEntry.id}). */
    private String entryId;

    /** Type of balance change: CREDIT (top-up), DEBIT (payment), REFUND (cancellation). */
    private WalletTransactionType transactionType;

    /** The absolute amount of this transaction (always positive; direction expressed via {@link #transactionType}). */
    private MoneyDto amount;

    /**
     * Opaque reference linking this entry to its source.
     * Formats: {@code "order:<orderId>"} (debit), {@code "refund:<orderId>"} (refund),
     * {@code "topup:<UUID>"} (credit).
     */
    private String referenceId;

    /** Human-readable description for customer-facing transaction history. */
    private String description;

    /** When this balance change occurred. Immutable. */
    private Instant occurredAt;
}
