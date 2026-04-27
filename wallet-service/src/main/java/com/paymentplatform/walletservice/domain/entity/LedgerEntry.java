package com.paymentplatform.walletservice.domain.entity;

import com.paymentplatform.commonlib.enums.WalletTransactionType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable ledger entry — the atomic unit of the wallet-service's event-sourced domain model.
 *
 * <p>Each row records a single balance change event with four key attributes:</p>
 * <ul>
 *   <li><strong>What happened</strong> — {@link #transactionType} (CREDIT, DEBIT, or REFUND)</li>
 *   <li><strong>How much</strong> — {@link #amount} (always positive; type determines direction)</li>
 *   <li><strong>The resulting balance</strong> — {@link #balanceAfter} snapshot for fast point-in-time queries
 *       without replaying the entire ledger</li>
 *   <li><strong>Why it happened</strong> — {@link #referenceId} links to the payment/order;
 *       {@link #description} is human-readable</li>
 * </ul>
 *
 * <p><strong>Immutability:</strong> Entries are never updated or deleted. There is no {@code @PreUpdate}.
 * {@link #occurredAt} is set once in {@link #onCreate()} and the {@code updatable=false} constraint
 * prevents JPA from overwriting it.</p>
 *
 * <p><strong>Idempotency:</strong> A unique constraint on {@code (wallet_id, reference_id, transaction_type)}
 * prevents duplicate entries for the same event (e.g. Kafka redelivery). The DB will throw
 * {@code DataIntegrityViolationException} which {@code WalletService} catches and treats as a
 * no-op (idempotent duplicate).</p>
 */
@Entity
@Table(name = "ledger_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LedgerEntry {

    /** Primary key — UUID assigned at persist time. */
    @Id
    private UUID id;

    /**
     * The wallet this entry belongs to. LAZY fetch — not needed when querying
     * entries directly; accessed only when navigating the wallet aggregate.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    /**
     * The type of balance change. Determines how to interpret this entry in reports:
     * CREDIT = incoming funds (top-up), DEBIT = payment charge, REFUND = order cancellation.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private WalletTransactionType transactionType;

    /**
     * The absolute amount of this entry — always positive.
     * Direction (credit vs debit) is expressed via {@link #transactionType}, not sign.
     * Stored as {@code DECIMAL(19,2)} for exact monetary arithmetic.
     */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    /**
     * ISO 4217 currency code for this entry.
     * Must match the wallet's currency — cross-currency entries are not currently supported.
     */
    @Column(nullable = false, length = 3)
    private String currency;

    /**
     * The wallet balance immediately after this entry was applied.
     * Stored as a snapshot to enable fast point-in-time balance queries
     * without replaying the entire ledger (O(1) vs O(n)).
     */
    @Column(name = "balance_after", nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    /**
     * Opaque reference linking this entry to its source event.
     * Formats used in practice:
     * <ul>
     *   <li>{@code "order:<orderId>"} — DEBIT from a payment</li>
     *   <li>{@code "refund:<orderId>"} — REFUND from order cancellation</li>
     *   <li>{@code "topup:<UUID>"} — CREDIT from a customer top-up</li>
     * </ul>
     * Combined with {@link #transactionType} this forms the unique idempotency key (DB constraint).
     */
    @Column(name = "reference_id", nullable = false)
    private String referenceId;

    /**
     * Human-readable description for customer-facing transaction history and support tooling.
     * Example: {@code "Payment for order abc12345"}.
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * When the balance change occurred. Immutable — set once in {@link #onCreate()}.
     * Used as the sort key for ledger queries ({@code ORDER BY occurred_at DESC}).
     */
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (occurredAt == null) occurredAt = Instant.now();
    }
}
