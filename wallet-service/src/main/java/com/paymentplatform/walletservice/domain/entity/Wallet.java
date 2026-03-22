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
 * Wallet aggregate root.
 *
 * Event sourcing model:
 *  - ledgerEntries is the source of truth (append-only log)
 *  - balance is a cached projection updated on every append
 *  - To rebuild balance: sum all ledger entries' signed amounts
 */
@Entity
@Table(name = "wallets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Wallet {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false, unique = true)
    private String customerId;

    @Column(nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "INR";

    @OneToMany(mappedBy = "wallet", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("occurredAt DESC")
    @Builder.Default
    private List<LedgerEntry> ledgerEntries = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

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
     * Credits the wallet and appends a ledger entry.
     * Returns the new entry for downstream use.
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
     * Debits the wallet if sufficient funds exist.
     * Throws InsufficientFundsException otherwise.
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
     * Refunds a previous debit. Functionally identical to credit
     * but tagged as REFUND for audit clarity.
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
