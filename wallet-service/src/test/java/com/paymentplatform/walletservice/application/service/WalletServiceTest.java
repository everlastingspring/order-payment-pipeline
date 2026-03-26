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
import com.paymentplatform.walletservice.domain.entity.LedgerEntry;
import com.paymentplatform.walletservice.domain.entity.Wallet;
import com.paymentplatform.walletservice.domain.repository.LedgerEntryRepository;
import com.paymentplatform.walletservice.domain.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WalletService")
class WalletServiceTest {

    @Mock private WalletRepository walletRepository;
    @Mock private LedgerEntryRepository ledgerEntryRepository;
    @Mock private WalletMapper walletMapper;

    private WalletService walletService;

    private static final String CUSTOMER_ID = "customer-001";
    private static final String ORDER_ID = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        walletService = new WalletService(walletRepository, ledgerEntryRepository, walletMapper);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Wallet buildWallet(UUID id, BigDecimal balance) {
        Wallet wallet = Wallet.builder()
                .customerId(CUSTOMER_ID)
                .balance(balance)
                .currency("INR")
                .ledgerEntries(new ArrayList<>())
                .build();
        setField(wallet, "id", id);
        return wallet;
    }

    private WalletDto stubWalletDto(UUID id, BigDecimal balance) {
        return WalletDto.builder()
                .walletId(id.toString())
                .customerId(CUSTOMER_ID)
                .balance(MoneyDto.builder().amount(balance).currency("INR").build())
                .build();
    }

    private void setField(Object target, String name, Object value) {
        try {
            var f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ── debitForPayment ───────────────────────────────────────────────────

    @Nested
    @DisplayName("debitForPayment")
    class DebitForPayment {

        @Test
        @DisplayName("happy path: debits wallet, returns updated DTO")
        void happyPath_debitsWallet() {
            UUID walletId = UUID.randomUUID();
            Wallet wallet = buildWallet(walletId, new BigDecimal("5000.00"));
            when(walletRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(wallet));
            when(ledgerEntryRepository.existsByWalletIdAndReferenceIdAndTransactionType(
                    walletId, "order:" + ORDER_ID, WalletTransactionType.DEBIT)).thenReturn(false);
            when(walletRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            WalletDto dto = stubWalletDto(walletId, new BigDecimal("903.00"));
            when(walletMapper.toDto(any())).thenReturn(dto);

            WalletDebitRequest request = WalletDebitRequest.builder()
                    .customerId(CUSTOMER_ID)
                    .orderId(ORDER_ID)
                    .amount(new BigDecimal("4097.00"))
                    .currency("INR")
                    .build();

            WalletDto result = walletService.debitForPayment(request);

            assertThat(result).isEqualTo(dto);
            assertThat(wallet.getBalance()).isEqualByComparingTo("903.00");
            assertThat(wallet.getLedgerEntries()).hasSize(1);
            verify(walletRepository).save(wallet);
        }

        @Test
        @DisplayName("idempotent: skips debit when referenceId already exists")
        void duplicateDebit_skipsProcessing() {
            UUID walletId = UUID.randomUUID();
            Wallet wallet = buildWallet(walletId, new BigDecimal("5000.00"));
            when(walletRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(wallet));
            when(ledgerEntryRepository.existsByWalletIdAndReferenceIdAndTransactionType(
                    walletId, "order:" + ORDER_ID, WalletTransactionType.DEBIT)).thenReturn(true);
            WalletDto dto = stubWalletDto(walletId, new BigDecimal("5000.00"));
            when(walletMapper.toDto(wallet)).thenReturn(dto);

            WalletDebitRequest request = WalletDebitRequest.builder()
                    .customerId(CUSTOMER_ID)
                    .orderId(ORDER_ID)
                    .amount(new BigDecimal("4097.00"))
                    .currency("INR")
                    .build();

            WalletDto result = walletService.debitForPayment(request);

            assertThat(result).isEqualTo(dto);
            // Balance unchanged — no debit happened
            assertThat(wallet.getBalance()).isEqualByComparingTo("5000.00");
            verify(walletRepository, never()).save(any());
        }

        @Test
        @DisplayName("insufficient funds: throws InsufficientFundsException")
        void insufficientFunds_throwsException() {
            UUID walletId = UUID.randomUUID();
            Wallet wallet = buildWallet(walletId, new BigDecimal("100.00"));
            when(walletRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(wallet));
            when(ledgerEntryRepository.existsByWalletIdAndReferenceIdAndTransactionType(
                    walletId, "order:" + ORDER_ID, WalletTransactionType.DEBIT)).thenReturn(false);

            WalletDebitRequest request = WalletDebitRequest.builder()
                    .customerId(CUSTOMER_ID)
                    .orderId(ORDER_ID)
                    .amount(new BigDecimal("4097.00"))
                    .currency("INR")
                    .build();

            assertThatThrownBy(() -> walletService.debitForPayment(request))
                    .isInstanceOf(InsufficientFundsException.class);
        }

        @Test
        @DisplayName("concurrent duplicate: DataIntegrityViolationException handled idempotently")
        void concurrentDuplicate_handledGracefully() {
            UUID walletId = UUID.randomUUID();
            Wallet wallet = buildWallet(walletId, new BigDecimal("5000.00"));
            when(walletRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(wallet));
            when(ledgerEntryRepository.existsByWalletIdAndReferenceIdAndTransactionType(
                    walletId, "order:" + ORDER_ID, WalletTransactionType.DEBIT)).thenReturn(false);
            when(walletRepository.save(any()))
                    .thenThrow(new DataIntegrityViolationException("duplicate"));
            when(walletRepository.findById(walletId)).thenReturn(Optional.of(wallet));
            WalletDto dto = stubWalletDto(walletId, new BigDecimal("5000.00"));
            when(walletMapper.toDto(wallet)).thenReturn(dto);

            WalletDebitRequest request = WalletDebitRequest.builder()
                    .customerId(CUSTOMER_ID)
                    .orderId(ORDER_ID)
                    .amount(new BigDecimal("4097.00"))
                    .currency("INR")
                    .build();

            WalletDto result = walletService.debitForPayment(request);

            assertThat(result).isEqualTo(dto);
        }

        @Test
        @DisplayName("creates wallet if customer has no wallet yet")
        void newCustomer_createsWallet() {
            UUID walletId = UUID.randomUUID();
            Wallet newWallet = buildWallet(walletId, BigDecimal.ZERO);
            // First: findByCustomerId returns empty → create
            // We need to simulate: findByCustomerId(empty) → save(new) → findByCustomerId(empty again for debit check)
            when(walletRepository.findByCustomerId(CUSTOMER_ID))
                    .thenReturn(Optional.empty())               // findOrCreateWallet lookup
                    .thenReturn(Optional.empty());              // won't be called again
            when(walletRepository.save(any())).thenAnswer(inv -> {
                Wallet w = inv.getArgument(0);
                if (w.getId() == null) {
                    setField(w, "id", walletId);
                }
                return w;
            });
            when(ledgerEntryRepository.existsByWalletIdAndReferenceIdAndTransactionType(
                    any(), any(), any())).thenReturn(false);
            when(walletMapper.toDto(any())).thenReturn(stubWalletDto(walletId, BigDecimal.ZERO));

            WalletDebitRequest request = WalletDebitRequest.builder()
                    .customerId(CUSTOMER_ID)
                    .orderId(ORDER_ID)
                    .amount(BigDecimal.ZERO) // zero amount to avoid insufficient funds
                    .currency("INR")
                    .build();

            walletService.debitForPayment(request);

            // save called at least once (for wallet creation + debit save)
            verify(walletRepository, atLeastOnce()).save(any());
        }
    }

    // ── topUp ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("topUp")
    class TopUp {

        @Test
        @DisplayName("credits wallet with specified amount and returns updated DTO")
        void happyPath_creditsWallet() {
            UUID walletId = UUID.randomUUID();
            Wallet wallet = buildWallet(walletId, new BigDecimal("1000.00"));
            when(walletRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(wallet));
            when(walletRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            WalletDto dto = stubWalletDto(walletId, new BigDecimal("6000.00"));
            when(walletMapper.toDto(any())).thenReturn(dto);

            WalletTopUpRequest request = WalletTopUpRequest.builder()
                    .amount(new BigDecimal("5000.00"))
                    .currency("INR")
                    .description("Test top-up")
                    .build();

            WalletDto result = walletService.topUp(CUSTOMER_ID, request);

            assertThat(result).isEqualTo(dto);
            assertThat(wallet.getBalance()).isEqualByComparingTo("6000.00");
            assertThat(wallet.getLedgerEntries()).hasSize(1);
        }

        @Test
        @DisplayName("defaults to INR currency and 'Wallet top-up' description when null")
        void defaultValues_usedWhenNull() {
            UUID walletId = UUID.randomUUID();
            Wallet wallet = buildWallet(walletId, BigDecimal.ZERO);
            when(walletRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(wallet));
            when(walletRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(walletMapper.toDto(any())).thenReturn(stubWalletDto(walletId, new BigDecimal("100.00")));

            WalletTopUpRequest request = WalletTopUpRequest.builder()
                    .amount(new BigDecimal("100.00"))
                    .currency(null)
                    .description(null)
                    .build();

            walletService.topUp(CUSTOMER_ID, request);

            assertThat(wallet.getLedgerEntries()).hasSize(1);
            LedgerEntry entry = wallet.getLedgerEntries().get(0);
            assertThat(entry.getDescription()).isEqualTo("Wallet top-up");
        }
    }

    // ── refundForCancellation ─────────────────────────────────────────────

    @Nested
    @DisplayName("refundForCancellation")
    class RefundForCancellation {

        @Test
        @DisplayName("refunds wallet for cancelled order with non-zero amount")
        void happyPath_refundsWallet() {
            UUID walletId = UUID.randomUUID();
            Wallet wallet = buildWallet(walletId, new BigDecimal("903.00"));
            when(walletRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(wallet));
            when(ledgerEntryRepository.existsByWalletIdAndReferenceIdAndTransactionType(
                    walletId, "refund:" + ORDER_ID, WalletTransactionType.REFUND)).thenReturn(false);
            when(walletRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            WalletDto dto = stubWalletDto(walletId, new BigDecimal("5000.00"));
            when(walletMapper.toDto(any())).thenReturn(dto);

            MoneyDto refundAmount = MoneyDto.builder()
                    .amount(new BigDecimal("4097.00"))
                    .currency("INR")
                    .build();

            WalletDto result = walletService.refundForCancellation(CUSTOMER_ID, refundAmount, ORDER_ID);

            assertThat(result).isEqualTo(dto);
            assertThat(wallet.getBalance()).isEqualByComparingTo("5000.00");
            verify(walletRepository).save(wallet);
        }

        @Test
        @DisplayName("zero refund amount: returns wallet without refund (no-op)")
        void zeroAmount_noRefund() {
            UUID walletId = UUID.randomUUID();
            Wallet wallet = buildWallet(walletId, new BigDecimal("5000.00"));
            when(walletRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(wallet));
            WalletDto dto = stubWalletDto(walletId, new BigDecimal("5000.00"));
            when(walletMapper.toDto(wallet)).thenReturn(dto);

            MoneyDto refundAmount = MoneyDto.builder()
                    .amount(BigDecimal.ZERO)
                    .currency("INR")
                    .build();

            WalletDto result = walletService.refundForCancellation(CUSTOMER_ID, refundAmount, ORDER_ID);

            assertThat(result).isEqualTo(dto);
            verify(walletRepository, never()).save(any());
        }

        @Test
        @DisplayName("null refund amount: returns wallet without refund (no-op)")
        void nullAmount_noRefund() {
            UUID walletId = UUID.randomUUID();
            Wallet wallet = buildWallet(walletId, new BigDecimal("5000.00"));
            when(walletRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(wallet));
            WalletDto dto = stubWalletDto(walletId, new BigDecimal("5000.00"));
            when(walletMapper.toDto(wallet)).thenReturn(dto);

            WalletDto result = walletService.refundForCancellation(CUSTOMER_ID, null, ORDER_ID);

            assertThat(result).isEqualTo(dto);
            verify(walletRepository, never()).save(any());
        }

        @Test
        @DisplayName("idempotent: skips refund when referenceId already exists")
        void duplicateRefund_skipsProcessing() {
            UUID walletId = UUID.randomUUID();
            Wallet wallet = buildWallet(walletId, new BigDecimal("5000.00"));
            when(walletRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(wallet));
            when(ledgerEntryRepository.existsByWalletIdAndReferenceIdAndTransactionType(
                    walletId, "refund:" + ORDER_ID, WalletTransactionType.REFUND)).thenReturn(true);
            WalletDto dto = stubWalletDto(walletId, new BigDecimal("5000.00"));
            when(walletMapper.toDto(wallet)).thenReturn(dto);

            MoneyDto refundAmount = MoneyDto.builder()
                    .amount(new BigDecimal("4097.00")).currency("INR").build();

            WalletDto result = walletService.refundForCancellation(CUSTOMER_ID, refundAmount, ORDER_ID);

            assertThat(result).isEqualTo(dto);
            verify(walletRepository, never()).save(any());
        }

        @Test
        @DisplayName("concurrent duplicate: DataIntegrityViolationException reloads wallet from DB")
        void concurrentDuplicate_reloadsFromDb() {
            UUID walletId = UUID.randomUUID();
            Wallet wallet = buildWallet(walletId, new BigDecimal("903.00"));
            Wallet reloadedWallet = buildWallet(walletId, new BigDecimal("903.00"));
            when(walletRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(wallet));
            when(ledgerEntryRepository.existsByWalletIdAndReferenceIdAndTransactionType(
                    walletId, "refund:" + ORDER_ID, WalletTransactionType.REFUND)).thenReturn(false);
            when(walletRepository.save(any()))
                    .thenThrow(new DataIntegrityViolationException("duplicate"));
            when(walletRepository.findById(walletId)).thenReturn(Optional.of(reloadedWallet));
            WalletDto dto = stubWalletDto(walletId, new BigDecimal("903.00"));
            when(walletMapper.toDto(reloadedWallet)).thenReturn(dto);

            MoneyDto refundAmount = MoneyDto.builder()
                    .amount(new BigDecimal("4097.00")).currency("INR").build();

            WalletDto result = walletService.refundForCancellation(CUSTOMER_ID, refundAmount, ORDER_ID);
            assertThat(result).isEqualTo(dto);
            // Verify it reloaded from DB, not using the mutated in-memory wallet
            verify(walletRepository).findById(walletId);
        }
    }

    // ── getBalance ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getBalance")
    class GetBalance {

        @Test
        @DisplayName("returns MoneyDto with current balance and currency")
        void found_returnsBalance() {
            UUID walletId = UUID.randomUUID();
            Wallet wallet = buildWallet(walletId, new BigDecimal("5000.00"));
            when(walletRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(wallet));

            MoneyDto result = walletService.getBalance(CUSTOMER_ID);

            assertThat(result.getAmount()).isEqualByComparingTo("5000.00");
            assertThat(result.getCurrency()).isEqualTo("INR");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when customer has no wallet")
        void notFound_throwsResourceNotFoundException() {
            when(walletRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> walletService.getBalance(CUSTOMER_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── getWallet ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getWallet")
    class GetWallet {

        @Test
        @DisplayName("returns mapped DTO when wallet found by ID")
        void found_returnsMappedDto() {
            UUID walletId = UUID.randomUUID();
            Wallet wallet = buildWallet(walletId, new BigDecimal("5000.00"));
            WalletDto dto = stubWalletDto(walletId, new BigDecimal("5000.00"));
            when(walletRepository.findById(walletId)).thenReturn(Optional.of(wallet));
            when(walletMapper.toDto(wallet)).thenReturn(dto);

            assertThat(walletService.getWallet(walletId)).isEqualTo(dto);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when wallet not found")
        void notFound_throwsResourceNotFoundException() {
            UUID walletId = UUID.randomUUID();
            when(walletRepository.findById(walletId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> walletService.getWallet(walletId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── getWalletByCustomer ───────────────────────────────────────────────

    @Nested
    @DisplayName("getWalletByCustomer")
    class GetWalletByCustomer {

        @Test
        @DisplayName("returns mapped DTO when wallet found by customerId")
        void found_returnsMappedDto() {
            UUID walletId = UUID.randomUUID();
            Wallet wallet = buildWallet(walletId, new BigDecimal("5000.00"));
            WalletDto dto = stubWalletDto(walletId, new BigDecimal("5000.00"));
            when(walletRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(wallet));
            when(walletMapper.toDto(wallet)).thenReturn(dto);

            assertThat(walletService.getWalletByCustomer(CUSTOMER_ID)).isEqualTo(dto);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when customer has no wallet")
        void notFound_throwsResourceNotFoundException() {
            when(walletRepository.findByCustomerId("unknown")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> walletService.getWalletByCustomer("unknown"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── getLedgerEntries ──────────────────────────────────────────────────

    @Nested
    @DisplayName("getLedgerEntries")
    class GetLedgerEntries {

        @Test
        @DisplayName("returns paged and mapped ledger entries")
        void returnsPagedEntries() {
            UUID walletId = UUID.randomUUID();
            Pageable pageable = PageRequest.of(0, 10);
            LedgerEntry entry = mock(LedgerEntry.class);
            LedgerEntryDto dto = LedgerEntryDto.builder().build();

            when(walletRepository.existsById(walletId)).thenReturn(true);
            when(ledgerEntryRepository.findByWalletIdOrderByOccurredAtDesc(walletId, pageable))
                    .thenReturn(new PageImpl<>(List.of(entry)));
            when(walletMapper.toEntryDto(entry)).thenReturn(dto);

            Page<LedgerEntryDto> result = walletService.getLedgerEntries(walletId, pageable);

            assertThat(result.getContent()).hasSize(1).containsExactly(dto);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when wallet does not exist")
        void walletNotFound_throwsResourceNotFoundException() {
            UUID walletId = UUID.randomUUID();
            when(walletRepository.existsById(walletId)).thenReturn(false);

            assertThatThrownBy(() -> walletService.getLedgerEntries(walletId, PageRequest.of(0, 10)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── rebuildBalance ────────────────────────────────────────────────────

    @Nested
    @DisplayName("rebuildBalance")
    class RebuildBalance {

        @Test
        @DisplayName("corrects wallet balance when cached and computed differ")
        void mismatch_correctsBalance() {
            UUID walletId = UUID.randomUUID();
            Wallet wallet = buildWallet(walletId, new BigDecimal("5000.00"));
            when(walletRepository.findById(walletId)).thenReturn(Optional.of(wallet));
            when(ledgerEntryRepository.computeBalance(walletId)).thenReturn(new BigDecimal("4500.00"));
            when(walletRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            WalletDto dto = stubWalletDto(walletId, new BigDecimal("4500.00"));
            when(walletMapper.toDto(any())).thenReturn(dto);

            WalletDto result = walletService.rebuildBalance(walletId);

            assertThat(wallet.getBalance()).isEqualByComparingTo("4500.00");
            verify(walletRepository).save(wallet);
            assertThat(result).isEqualTo(dto);
        }

        @Test
        @DisplayName("no correction needed when cached and computed match")
        void match_noSave() {
            UUID walletId = UUID.randomUUID();
            Wallet wallet = buildWallet(walletId, new BigDecimal("5000.00"));
            when(walletRepository.findById(walletId)).thenReturn(Optional.of(wallet));
            when(ledgerEntryRepository.computeBalance(walletId)).thenReturn(new BigDecimal("5000.00"));
            WalletDto dto = stubWalletDto(walletId, new BigDecimal("5000.00"));
            when(walletMapper.toDto(wallet)).thenReturn(dto);

            WalletDto result = walletService.rebuildBalance(walletId);

            verify(walletRepository, never()).save(any());
            assertThat(result).isEqualTo(dto);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when wallet not found")
        void notFound_throwsResourceNotFoundException() {
            UUID walletId = UUID.randomUUID();
            when(walletRepository.findById(walletId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> walletService.rebuildBalance(walletId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
