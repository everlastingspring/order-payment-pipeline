package com.paymentplatform.walletservice.domain.entity;

import com.paymentplatform.commonlib.enums.WalletTransactionType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable ledger entry — the atomic unit of event sourcing.
 *
 * Each entry records a single balance change with:
 *  - What happened (transactionType)
 *  - How much (amount)
 *  - The resulting balance (balanceAfter) — snapshot for fast point-in-time queries
 *  - Why it happened (referenceId links to payment/order, description is human-readable)
 *
 * Entries are never updated or deleted.
 * The unique constraint (wallet_id, reference_id, transaction_type) provides idempotency.
 */
@Entity
@Table(name = "ledger_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LedgerEntry {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private WalletTransactionType transactionType;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "reference_id", nullable = false)
    private String referenceId;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (occurredAt == null) occurredAt = Instant.now();
    }
}
