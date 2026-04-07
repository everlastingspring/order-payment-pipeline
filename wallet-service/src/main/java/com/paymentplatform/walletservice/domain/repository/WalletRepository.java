package com.paymentplatform.walletservice.domain.repository;

import com.paymentplatform.walletservice.domain.entity.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    Optional<Wallet> findByCustomerId(String customerId);

    boolean existsByCustomerId(String customerId);

    /**
     * Fetches the wallet with a DB-level exclusive row lock (SELECT ... FOR UPDATE).
     *
     * Use this before any balance mutation (debit / credit / refund) inside a
     * @Transactional method. The lock is held until the transaction commits, so
     * concurrent balance operations for the same customer queue at the DB rather
     * than racing in application code.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.customerId = :customerId")
    Optional<Wallet> findByCustomerIdForUpdate(@Param("customerId") String customerId);

    /**
     * Same as above but keyed by wallet ID.
     * Used by rebuildBalance() which operates on a known wallet UUID.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.id = :id")
    Optional<Wallet> findByIdForUpdate(@Param("id") UUID id);
}
