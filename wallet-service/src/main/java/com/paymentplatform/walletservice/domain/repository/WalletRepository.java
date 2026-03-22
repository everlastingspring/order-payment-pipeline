package com.paymentplatform.walletservice.domain.repository;

import com.paymentplatform.walletservice.domain.entity.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    Optional<Wallet> findByCustomerId(String customerId);

    boolean existsByCustomerId(String customerId);
}
