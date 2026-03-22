package com.paymentplatform.walletservice.application.service;

import com.paymentplatform.commonlib.dto.LedgerEntryDto;
import com.paymentplatform.commonlib.dto.WalletDto;
import com.paymentplatform.commonlib.enums.WalletTransactionType;
import com.paymentplatform.commonlib.events.PaymentCompletedEvent;
import com.paymentplatform.commonlib.exception.ResourceNotFoundException;
import com.paymentplatform.walletservice.application.mapper.WalletMapper;
import com.paymentplatform.walletservice.domain.entity.LedgerEntry;
import com.paymentplatform.walletservice.domain.entity.Wallet;
import com.paymentplatform.walletservice.domain.repository.LedgerEntryRepository;
import com.paymentplatform.walletservice.domain.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {

    private final WalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final WalletMapper walletMapper;

    /**
     * Credits a customer's wallet when their payment completes.
     * Creates the wallet on first use (lazy initialization).
     *
     * Idempotency is enforced by the unique constraint
     * (wallet_id, reference_id, transaction_type) on ledger_entries.
     * If a duplicate payment.completed event arrives, the DB insert
     * fails and we skip silently.
     */
    @Transactional
    public void creditFromPayment(PaymentCompletedEvent event) {
        String customerId = event.getCustomerId();
        String referenceId = "payment:" + event.getPaymentId();
        BigDecimal amount = event.getAmount().getAmount();
        String currency = event.getAmount().getCurrency();

        // Find or create wallet
        Wallet wallet = walletRepository.findByCustomerId(customerId)
                .orElseGet(() -> {
                    log.info("Creating new wallet for customer: {}", customerId);
                    Wallet newWallet = Wallet.builder()
                            .customerId(customerId)
                            .currency(currency)
                            .build();
                    return walletRepository.save(newWallet);
                });

        // Idempotency check via ledger
        if (ledgerEntryRepository.existsByWalletIdAndReferenceIdAndTransactionType(
                wallet.getId(), referenceId, WalletTransactionType.CREDIT)) {
            log.info("Duplicate credit skipped: walletId={}, referenceId={}",
                    wallet.getId(), referenceId);
            return;
        }

        // Append ledger entry (event sourcing: this IS the state change)
        LedgerEntry entry = wallet.credit(
                amount,
                referenceId,
                "Payment completed for order " + event.getOrderId()
        );

        walletRepository.save(wallet);
        log.info("Wallet credited: walletId={}, amount={} {}, newBalance={}, referenceId={}",
                wallet.getId(), amount, currency, wallet.getBalance(), referenceId);
    }

    // ── Read operations ──

    @Transactional(readOnly = true)
    public WalletDto getWallet(UUID walletId) {
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet", walletId));
        return walletMapper.toDto(wallet);
    }

    @Transactional(readOnly = true)
    public WalletDto getWalletByCustomer(String customerId) {
        Wallet wallet = walletRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet", customerId));
        return walletMapper.toDto(wallet);
    }

    @Transactional(readOnly = true)
    public Page<LedgerEntryDto> getLedgerEntries(UUID walletId, Pageable pageable) {
        if (!walletRepository.existsById(walletId)) {
            throw new ResourceNotFoundException("Wallet", walletId);
        }
        return ledgerEntryRepository.findByWalletIdOrderByOccurredAtDesc(walletId, pageable)
                .map(walletMapper::toEntryDto);
    }

    /**
     * Rebuilds wallet balance from the ledger entries (event sourcing replay).
     * Useful for consistency checks or after data recovery.
     */
    @Transactional
    public WalletDto rebuildBalance(UUID walletId) {
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet", walletId));

        BigDecimal computed = ledgerEntryRepository.computeBalance(walletId);
        BigDecimal cached = wallet.getBalance();

        if (computed.compareTo(cached) != 0) {
            log.warn("Balance mismatch detected for wallet {}: cached={}, computed={}. Correcting.",
                    walletId, cached, computed);
            wallet.setBalance(computed);
            walletRepository.save(wallet);
        }

        return walletMapper.toDto(wallet);
    }
}
