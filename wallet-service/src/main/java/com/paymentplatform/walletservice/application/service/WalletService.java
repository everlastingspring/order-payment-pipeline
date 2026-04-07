package com.paymentplatform.walletservice.application.service;

import com.paymentplatform.commonlib.dto.LedgerEntryDto;
import com.paymentplatform.commonlib.dto.MoneyDto;
import com.paymentplatform.commonlib.dto.WalletDebitRequest;
import com.paymentplatform.commonlib.dto.WalletDto;
import com.paymentplatform.commonlib.dto.WalletTopUpRequest;
import com.paymentplatform.commonlib.enums.WalletTransactionType;
import com.paymentplatform.commonlib.exception.InsufficientFundsException;
import com.paymentplatform.commonlib.exception.ResourceNotFoundException;
import com.paymentplatform.walletservice.application.mapper.WalletMapper;
import com.paymentplatform.walletservice.domain.entity.Wallet;
import com.paymentplatform.walletservice.domain.repository.LedgerEntryRepository;
import com.paymentplatform.walletservice.domain.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
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

    // ── Payment debit (called synchronously by payment-service via REST) ──

    /**
     * Debits the customer's wallet for an order payment.
     *
     * Called by payment-service DURING payment processing (before payment.completed is published).
     * This is the single source of truth for the wallet balance change.
     *
     * Idempotency: orderId is used as the ledger referenceId.
     * Duplicate calls for the same orderId are skipped silently.
     *
     * @throws InsufficientFundsException if balance < amount
     */
    @Transactional
    public WalletDto debitForPayment(WalletDebitRequest request) {
        String customerId = request.getCustomerId();
        String referenceId = "order:" + request.getOrderId();
        BigDecimal amount = request.getAmount();
        String currency = request.getCurrency();

        // Ensure wallet row exists before locking (creation is idempotent)
        findOrCreateWallet(customerId, currency);

        // Acquire exclusive row lock — blocks any concurrent debit/credit for this customer
        // until this transaction commits. Prevents lost-update on balance.
        Wallet wallet = walletRepository.findByCustomerIdForUpdate(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet", customerId));

        // Idempotency — skip if this order was already debited
        if (ledgerEntryRepository.existsByWalletIdAndReferenceIdAndTransactionType(
                wallet.getId(), referenceId, WalletTransactionType.DEBIT)) {
            log.info("Duplicate debit skipped (idempotent): walletId={}, referenceId={}",
                    wallet.getId(), referenceId);
            return walletMapper.toDto(wallet);
        }

        // Will throw InsufficientFundsException if balance < amount
        try {
            wallet.debit(amount, referenceId, "Payment for order " + request.getOrderId());
            walletRepository.save(wallet);
            log.info("Wallet debited: walletId={}, customerId={}, amount={} {}, newBalance={}, orderId={}",
                    wallet.getId(), customerId, amount, currency, wallet.getBalance(), request.getOrderId());
            return walletMapper.toDto(wallet);
        } catch (DataIntegrityViolationException e) {
            // Concurrent duplicate insert — idempotent
            log.info("Duplicate debit caught by DB constraint: walletId={}, referenceId={}",
                    wallet.getId(), referenceId);
            return walletMapper.toDto(walletRepository.findById(wallet.getId()).orElseThrow());
        }
    }

    // ── Top-up (customer adds money to wallet) ──

    /**
     * Credits a customer's wallet (top-up).
     * Creates the wallet if it doesn't exist yet.
     */
    @Transactional
    public WalletDto topUp(String customerId, WalletTopUpRequest request) {
        BigDecimal amount = request.getAmount();
        String currency = request.getCurrency() != null ? request.getCurrency() : "INR";
        String description = request.getDescription() != null
                ? request.getDescription()
                : "Wallet top-up";
        String referenceId = "topup:" + UUID.randomUUID();

        // Ensure wallet exists before locking
        findOrCreateWallet(customerId, currency);

        // Acquire exclusive row lock — concurrent top-ups must queue, not race
        Wallet wallet = walletRepository.findByCustomerIdForUpdate(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet", customerId));

        wallet.credit(amount, referenceId, description);
        walletRepository.save(wallet);

        log.info("Wallet topped up: walletId={}, customerId={}, amount={} {}, newBalance={}",
                wallet.getId(), customerId, amount, currency, wallet.getBalance());

        return walletMapper.toDto(wallet);
    }

    // ── Refund on order cancellation ──

    /**
     * Refunds a customer's wallet when their order is cancelled after payment.
     *
     * Called by OrderEventConsumer when order.cancelled is received with a non-zero refundAmount.
     * Idempotent: uses "refund:{orderId}" as referenceId — duplicate events are skipped.
     */
    @Transactional
    public WalletDto refundForCancellation(String customerId, MoneyDto refundAmount, String orderId) {
        if (refundAmount == null || refundAmount.getAmount() == null
                || refundAmount.getAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            log.info("No refund needed for cancelled order: orderId={}, customerId={}", orderId, customerId);
            return getWalletByCustomer(customerId);
        }

        String referenceId = "refund:" + orderId;
        String currency = refundAmount.getCurrency() != null ? refundAmount.getCurrency() : "INR";

        // Ensure wallet exists before locking
        findOrCreateWallet(customerId, currency);

        // Acquire exclusive row lock — prevents concurrent refund/debit race on same wallet
        Wallet wallet = walletRepository.findByCustomerIdForUpdate(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet", customerId));

        // Idempotency — skip if already refunded
        if (ledgerEntryRepository.existsByWalletIdAndReferenceIdAndTransactionType(
                wallet.getId(), referenceId, WalletTransactionType.REFUND)) {
            log.info("Duplicate refund skipped: walletId={}, referenceId={}", wallet.getId(), referenceId);
            return walletMapper.toDto(wallet);
        }

        try {
            wallet.refund(refundAmount.getAmount(), referenceId,
                    "Refund for cancelled order " + orderId);
            walletRepository.save(wallet);
            log.info("Wallet refunded: walletId={}, customerId={}, amount={} {}, newBalance={}, orderId={}",
                    wallet.getId(), customerId,
                    refundAmount.getAmount(), refundAmount.getCurrency(),
                    wallet.getBalance(), orderId);
        } catch (DataIntegrityViolationException e) {
            log.info("Duplicate refund caught by DB constraint: walletId={}, referenceId={}",
                    wallet.getId(), referenceId);
            return walletMapper.toDto(walletRepository.findById(wallet.getId()).orElseThrow());
        }

        return walletMapper.toDto(wallet);
    }

    // ── Read operations ──

    /**
     * Lightweight balance check — returns just the current balance.
     * Useful for pre-order checks in the UI.
     */
    @Transactional(readOnly = true)
    public MoneyDto getBalance(String customerId) {
        Wallet wallet = walletRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet", customerId));
        return MoneyDto.builder()
                .amount(wallet.getBalance())
                .currency(wallet.getCurrency())
                .build();
    }

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

    // ── Balance rebuild (event sourcing replay) ──

    /**
     * Recomputes wallet balance from ledger entries.
     * Useful for consistency checks or after data recovery.
     */
    @Transactional
    public WalletDto rebuildBalance(UUID walletId) {
        // Lock the wallet row — prevents a concurrent payment overwriting the corrected balance
        Wallet wallet = walletRepository.findByIdForUpdate(walletId)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet", walletId));

        BigDecimal computed = ledgerEntryRepository.computeBalance(walletId);
        BigDecimal cached = wallet.getBalance();

        if (computed.compareTo(cached) != 0) {
            log.warn("Balance mismatch for wallet {}: cached={}, computed={}. Correcting.",
                    walletId, cached, computed);
            wallet.setBalance(computed);
            walletRepository.save(wallet);
        }

        return walletMapper.toDto(wallet);
    }

    // ── Internal helpers ──

    private Wallet findOrCreateWallet(String customerId, String currency) {
        return walletRepository.findByCustomerId(customerId)
                .orElseGet(() -> {
                    try {
                        log.info("Creating wallet for customer: {}", customerId);
                        Wallet newWallet = Wallet.builder()
                                .customerId(customerId)
                                .currency(currency)
                                .build();
                        return walletRepository.save(newWallet);
                    } catch (DataIntegrityViolationException e) {
                        log.info("Wallet already created concurrently for customer: {}", customerId);
                        return walletRepository.findByCustomerId(customerId)
                                .orElseThrow(() -> new IllegalStateException(
                                        "Wallet race condition: disappeared for customer " + customerId));
                    }
                });
    }
}
