package com.paymentplatform.walletservice.integration;

import com.paymentplatform.commonlib.dto.MoneyDto;
import com.paymentplatform.commonlib.dto.WalletDebitRequest;
import com.paymentplatform.commonlib.dto.WalletTopUpRequest;
import com.paymentplatform.commonlib.enums.WalletTransactionType;
import com.paymentplatform.walletservice.application.service.WalletService;
import com.paymentplatform.walletservice.domain.entity.LedgerEntry;
import com.paymentplatform.walletservice.domain.entity.Wallet;
import com.paymentplatform.walletservice.domain.repository.LedgerEntryRepository;
import com.paymentplatform.walletservice.domain.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.kafka.listener.auto-startup=false",
                "spring.kafka.admin.fail-fast=false",
                "management.tracing.enabled=false",
                "management.metrics.export.prometheus.enabled=false"
        }
)
@Testcontainers(disabledWithoutDocker = false)
class WalletServiceIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("wallet_db")
                    .withUsername("test")
                    .withPassword("test");

    @Container
    private static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.4"));

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    private static final String CUSTOMER_ID = "customer-it-001";
    private static final String ORDER_ID = "order-it-001";

    @Autowired
    private WalletService walletService;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Test
    @Transactional
    void topUpDebitAndRefund_persistExpectedLedgerTrail() {
        walletService.topUp(CUSTOMER_ID, WalletTopUpRequest.builder()
                .amount(new BigDecimal("5000.00"))
                .currency("INR")
                .description("Integration seed")
                .build());

        walletService.debitForPayment(WalletDebitRequest.builder()
                .customerId(CUSTOMER_ID)
                .amount(new BigDecimal("4097.00"))
                .currency("INR")
                .orderId(ORDER_ID)
                .build());

        walletService.refundForCancellation(
                CUSTOMER_ID,
                MoneyDto.builder().amount(new BigDecimal("4097.00")).currency("INR").build(),
                ORDER_ID
        );

        Wallet wallet = walletRepository.findByCustomerId(CUSTOMER_ID).orElseThrow();
        List<LedgerEntry> entries = ledgerEntryRepository
                .findByWalletIdOrderByOccurredAtDesc(wallet.getId(), Pageable.unpaged())
                .getContent();

        assertThat(wallet.getBalance()).isEqualByComparingTo("5000.00");
        assertThat(ledgerEntryRepository.computeBalance(wallet.getId())).isEqualByComparingTo("5000.00");
        assertThat(entries).hasSize(3);
        assertThat(entries)
                .extracting(LedgerEntry::getTransactionType)
                .containsExactlyInAnyOrder(
                        WalletTransactionType.CREDIT,
                        WalletTransactionType.DEBIT,
                        WalletTransactionType.REFUND
                );
    }

    @Test
    @Transactional
    void duplicateDebitForSameOrder_isIgnoredAfterFirstWrite() {
        walletService.topUp(CUSTOMER_ID, WalletTopUpRequest.builder()
                .amount(new BigDecimal("5000.00"))
                .currency("INR")
                .description("Integration seed")
                .build());

        WalletDebitRequest request = WalletDebitRequest.builder()
                .customerId(CUSTOMER_ID)
                .amount(new BigDecimal("4097.00"))
                .currency("INR")
                .orderId(ORDER_ID)
                .build();

        walletService.debitForPayment(request);
        walletService.debitForPayment(request);

        Wallet wallet = walletRepository.findByCustomerId(CUSTOMER_ID).orElseThrow();
        List<LedgerEntry> entries = ledgerEntryRepository
                .findByWalletIdOrderByOccurredAtDesc(wallet.getId(), Pageable.unpaged())
                .getContent();

        assertThat(wallet.getBalance()).isEqualByComparingTo("903.00");
        assertThat(entries).hasSize(2);
        assertThat(entries)
                .filteredOn(entry -> entry.getTransactionType() == WalletTransactionType.DEBIT)
                .singleElement()
                .extracting(LedgerEntry::getReferenceId)
                .isEqualTo("order:" + ORDER_ID);
    }

    @Test
    void concurrentCreditsAndDebits_maintainConsistentBalance() throws InterruptedException {
        // Setup: Create and top up wallets for two independent customers
        String customer1 = "customer-concurrent-001";
        String customer2 = "customer-concurrent-002";

        walletService.topUp(customer1, WalletTopUpRequest.builder()
                .amount(new BigDecimal("5000.00"))
                .currency("INR")
                .description("Concurrent customer 1")
                .build());

        walletService.topUp(customer2, WalletTopUpRequest.builder()
                .amount(new BigDecimal("5000.00"))
                .currency("INR")
                .description("Concurrent customer 2")
                .build());

        // Concurrent debits on DIFFERENT wallets (thread-safe)
        WalletDebitRequest request1 = WalletDebitRequest.builder()
                .customerId(customer1)
                .amount(new BigDecimal("1000.00"))
                .currency("INR")
                .orderId("order-concurrent-001")
                .build();

        WalletDebitRequest request2 = WalletDebitRequest.builder()
                .customerId(customer2)
                .amount(new BigDecimal("2000.00"))
                .currency("INR")
                .orderId("order-concurrent-002")
                .build();

        // Capture exceptions inside lambdas so they don't reach Java's uncaught-exception
        // handler, which would print "Exception in thread..." to stderr and produce a
        // spurious GitHub Actions error annotation even when the test passes.
        AtomicReference<Throwable> error1 = new AtomicReference<>();
        AtomicReference<Throwable> error2 = new AtomicReference<>();

        Thread thread1 = new Thread(() -> {
            try {
                walletService.debitForPayment(request1);
            } catch (Exception e) {
                error1.set(e);
            }
        });
        Thread thread2 = new Thread(() -> {
            try {
                walletService.debitForPayment(request2);
            } catch (Exception e) {
                error2.set(e);
            }
        });

        thread1.start();
        thread2.start();
        thread1.join();
        thread2.join();

        // Both debits target different wallets — neither should fail
        assertThat(error1.get()).isNull();
        assertThat(error2.get()).isNull();

        // Verify both wallets have correct balances
        Wallet wallet1 = walletRepository.findByCustomerId(customer1).orElseThrow();
        Wallet wallet2 = walletRepository.findByCustomerId(customer2).orElseThrow();

        assertThat(wallet1.getBalance()).isEqualByComparingTo("4000.00");
        assertThat(wallet2.getBalance()).isEqualByComparingTo("3000.00");

        // Verify ledger entries for both wallets
        List<LedgerEntry> entries1 = ledgerEntryRepository
                .findByWalletIdOrderByOccurredAtDesc(wallet1.getId(), Pageable.unpaged())
                .getContent();
        List<LedgerEntry> entries2 = ledgerEntryRepository
                .findByWalletIdOrderByOccurredAtDesc(wallet2.getId(), Pageable.unpaged())
                .getContent();

        assertThat(entries1).hasSizeGreaterThanOrEqualTo(2); // CREDIT + DEBIT
        assertThat(entries2).hasSizeGreaterThanOrEqualTo(2); // CREDIT + DEBIT
    }
}
