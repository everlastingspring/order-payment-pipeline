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

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    Page<LedgerEntry> findByWalletIdOrderByOccurredAtDesc(UUID walletId, Pageable pageable);

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
