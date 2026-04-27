package com.paymentplatform.walletservice.domain.entity;

import com.paymentplatform.commonlib.enums.WalletTransactionType;
import com.paymentplatform.commonlib.exception.InsufficientFundsException;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Wallet aggregate root — the central entity of the wallet-service's event-sourced domain model.
 *
 * <p><strong>Event sourcing model:</strong></p>
 * <ul>
 *   <li>{@link #ledgerEntries} is the source of truth — an append-only log of every balance change.</li>
 *   <li>{@link #balance} is a cached projection updated on every {@link #credit}/{@link #debit}/{@link #refund}.
 *       It avoids summing all ledger entries on every read but can be reconstructed via
 *       {@code WalletService.rebuildBalance()} if it drifts.</li>
 * </ul>
 *
 * <p><strong>Concurrency safety:</strong> Callers must acquire a row-level lock with
 * {@code walletRepository.findByCustomerIdForUpdate()} before calling any mutation method.
 * Without the lock, two concurrent transactions could each read the same balance, both pass the
 * funds check, and produce an overdraft (lost-update problem).</p>
 *
 * <p><strong>Lazy wallet creation:</strong> Wallets are created on first access by
 * {@code WalletService.findOrCreateWallet()} — there is no explicit "create wallet" API.
 * A customer's wallet is created automatically when they first top up or when a payment is
 * attempted against their account.</p>
 */
@Entity
@Table(name = "wallets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Wallet {

    /** Primary key — UUID assigned at persist time. */
    @Id
    private UUID id;

    /**
     * Unique customer identifier. One wallet per customer (enforced by unique constraint).
     * Maps to the {@code customerId} propagated through all order/payment events.
     */
    @Column(name = "customer_id", nullable = false, unique = true)
    private String customerId;

    /**
     * Cached balance projection — the sum of all ledger entry amounts for this wallet.
     * Stored as {@code DECIMAL(19,2)} to prevent floating-point rounding errors on monetary values.
     * Starts at zero for a new wallet; updated atomically with each ledger append.
     */
    @Column(nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;

    /**
     * ISO 4217 currency code for all ledger entries in this wallet.
     * Defaults to {@code "INR"} — multi-currency support would require per-entry currency handling.
     */
    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "INR";

    /**
     * Ordered collection of all balance-change events (newest first).
     * Cascades all operations — saving the wallet saves any new entries added by
     * {@link #credit}/{@link #debit}/{@link #refund}.
     */
    @OneToMany(mappedBy = "wallet", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("occurredAt DESC")
    @Builder.Default
    private List<LedgerEntry> ledgerEntries = new ArrayList<>();

    /** Immutable creation timestamp. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Last-updated timestamp. Refreshed on every balance mutation. */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Credits the wallet and appends a {@code CREDIT} ledger entry.
     *
     * <p>Used for customer top-ups. The balance is increased atomically with the entry append.
     * The entry is added to {@link #ledgerEntries} and will be persisted when the wallet is saved.</p>
     *
     * @param amount      the positive amount to add; must not be null or negative
     * @param referenceId unique reference for idempotency (e.g. {@code "topup:<UUID>"})
     * @param description human-readable description for the ledger audit trail
     * @return the newly created {@link LedgerEntry} (not yet persisted independently)
     */
    public LedgerEntry credit(BigDecimal amount, String referenceId, String description) {
        this.balance = this.balance.add(amount);

        LedgerEntry entry = LedgerEntry.builder()
                .wallet(this)
                .transactionType(WalletTransactionType.CREDIT)
                .amount(amount)
                .currency(this.currency)
                .balanceAfter(this.balance)
                .referenceId(referenceId)
                .description(description)
                .build();

        this.ledgerEntries.add(entry);
        return entry;
    }

    /**
     * Debits the wallet if sufficient funds exist and appends a {@code DEBIT} ledger entry.
     *
     * <p>Called by {@code WalletService.debitForPayment()} after a row-level lock is acquired.
     * The balance check and update are atomic within the transaction.</p>
     *
     * @param amount      the amount to deduct; must be positive and ≤ current balance
     * @param referenceId unique reference for idempotency (e.g. {@code "order:<orderId>"})
     * @param description human-readable description for the ledger audit trail
     * @return the newly created {@link LedgerEntry}
     * @throws InsufficientFundsException if {@link #balance} is less than {@code amount}
     */
    public LedgerEntry debit(BigDecimal amount, String referenceId, String description) {
        if (this.balance.compareTo(amount) < 0) {
            throw new InsufficientFundsException(
                    this.id.toString(), amount, this.balance);
        }

        this.balance = this.balance.subtract(amount);

        LedgerEntry entry = LedgerEntry.builder()
                .wallet(this)
                .transactionType(WalletTransactionType.DEBIT)
                .amount(amount)
                .currency(this.currency)
                .balanceAfter(this.balance)
                .referenceId(referenceId)
                .description(description)
                .build();

        this.ledgerEntries.add(entry);
        return entry;
    }

    /**
     * Refunds the wallet and appends a {@code REFUND} ledger entry.
     *
     * <p>Functionally equivalent to {@link #credit} — the balance is increased by {@code amount}.
     * Tagged as {@code REFUND} rather than {@code CREDIT} for audit clarity: a REFUND can be
     * distinguished from a regular top-up in ledger reports and customer-facing transaction history.
     * See {@link com.paymentplatform.commonlib.enums.WalletTransactionType} for the distinction rationale.</p>
     *
     * <p>Called by {@code WalletService.refundForCancellation()} when {@code order.cancelled} is
     * received with a non-zero {@code refundAmount}.</p>
     *
     * @param amount      the positive amount to refund
     * @param referenceId unique reference for idempotency (e.g. {@code "refund:<orderId>"})
     * @param description human-readable description for the ledger audit trail
     * @return the newly created {@link LedgerEntry}
     */
    public LedgerEntry refund(BigDecimal amount, String referenceId, String description) {
        this.balance = this.balance.add(amount);

        LedgerEntry entry = LedgerEntry.builder()
                .wallet(this)
                .transactionType(WalletTransactionType.REFUND)
                .amount(amount)
                .currency(this.currency)
                .balanceAfter(this.balance)
                .referenceId(referenceId)
                .description(description)
                .build();

        this.ledgerEntries.add(entry);
        return entry;
    }
}
