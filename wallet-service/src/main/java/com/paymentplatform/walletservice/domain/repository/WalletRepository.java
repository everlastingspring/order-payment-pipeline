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

/**
 * Spring Data JPA repository for {@link Wallet} entities.
 *
 * <p>Wallets use an event-sourced balance model — the {@code balance} column is a cached
 * projection of the ledger. Any write path that mutates the balance must use a
 * pessimistic write lock (see {@link #findByCustomerIdForUpdate} and
 * {@link #findByIdForUpdate}) to prevent lost-update races under concurrent requests.</p>
 *
 * <p>Wallets are created lazily on first interaction via
 * {@code WalletService.findOrCreateWallet()}. The {@link #existsByCustomerId} check is
 * used to avoid duplicate creation under concurrent first-access.</p>
 */
@Repository
public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    /**
     * Looks up the wallet for a customer without acquiring a lock.
     * Safe for read-only operations (balance query, ledger display).
     * For mutations use {@link #findByCustomerIdForUpdate} instead.
     *
     * @param customerId external customer identifier
     * @return the customer's wallet, or empty if not yet created
     */
    Optional<Wallet> findByCustomerId(String customerId);

    /**
     * Checks whether a wallet already exists for the given customer.
     * Used in the lazy-creation guard before attempting to insert a new wallet.
     *
     * @param customerId external customer identifier
     * @return {@code true} if the customer already has a wallet
     */
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
