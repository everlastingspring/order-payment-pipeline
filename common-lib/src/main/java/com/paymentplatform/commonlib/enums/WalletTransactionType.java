package com.paymentplatform.commonlib.enums;

/**
 * Type of balance movement recorded in a wallet ledger entry.
 *
 * <p>Decision: {@code REFUND} is kept distinct from {@code CREDIT} even though
 * both increase the balance. The distinction enables clean financial reporting —
 * "refunds issued this month" vs "top-ups received this month" — without needing
 * to parse the {@code description} field.</p>
 */
public enum WalletTransactionType {

    /** Money added to the wallet (top-up, promotion credit). Balance increases. */
    CREDIT,

    /** Money removed from the wallet for an order payment. Balance decreases. */
    DEBIT,

    /** Money restored after an order cancellation. Balance increases. Distinct from CREDIT for audit clarity. */
    REFUND,

    /** Manual balance correction (future admin use — not currently set by live code). */
    ADJUSTMENT
}
