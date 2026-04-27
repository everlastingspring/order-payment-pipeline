package com.paymentplatform.walletservice.domain.repository;

import com.paymentplatform.commonlib.enums.WalletTransactionType;
import com.paymentplatform.walletservice.domain.entity.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link LedgerEntry} entities.
 *
 * <p>The ledger is an append-only log of every wallet mutation. Individual rows are never
 * updated or deleted; balance is derived from the aggregate of all entries per wallet
 * (see {@link #computeBalance(UUID)}).</p>
 *
 * <p>The unique constraint on {@code (walletId, referenceId, transactionType)} enforces
 * idempotency at the DB level — duplicate inserts for the same reference (e.g. Kafka
 * redelivery) are rejected, preventing double-accounting.</p>
 */
@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    /**
     * Returns all ledger entries for a given wallet, newest first.
     * Used by the wallet ledger endpoint to display paginated transaction history.
     *
     * @param walletId the wallet whose ledger is to be retrieved
     * @param pageable pagination and sorting parameters
     * @return page of ledger entries ordered by {@code occurredAt DESC}
     */
    Page<LedgerEntry> findByWalletIdOrderByOccurredAtDesc(UUID walletId, Pageable pageable);

    /**
     * Idempotency check — returns {@code true} if a ledger entry with the same wallet,
     * reference, and transaction type combination already exists. Called before inserting
     * to detect Kafka redelivery and skip duplicate processing.
     *
     * @param walletId        the wallet identifier
     * @param referenceId     the business object reference (orderId, paymentId)
     * @param transactionType the type of transaction (DEBIT, CREDIT, REFUND, ADJUSTMENT)
     * @return {@code true} if this entry has already been recorded
     */
    boolean existsByWalletIdAndReferenceIdAndTransactionType(
            UUID walletId, String referenceId, WalletTransactionType transactionType);

    /**
     * Rebuilds wallet balance from ledger entries.
     * CREDIT and REFUND add, DEBIT subtracts.
     */
    @Query("""
            SELECT COALESCE(
                SUM(CASE
                    WHEN e.transactionType IN ('CREDIT', 'REFUND', 'ADJUSTMENT') THEN e.amount
                    WHEN e.transactionType = 'DEBIT' THEN -e.amount
                    ELSE 0
                END), 0)
            FROM LedgerEntry e WHERE e.wallet.id = :walletId
            """)
    BigDecimal computeBalance(UUID walletId);
}
