package com.paymentplatform.paymentservice.integration;

import com.paymentplatform.commonlib.constants.KafkaTopics;
import com.paymentplatform.commonlib.dto.MoneyDto;
import com.paymentplatform.commonlib.enums.PaymentMethod;
import com.paymentplatform.commonlib.enums.PaymentStatus;
import com.paymentplatform.commonlib.events.InventoryReservedEvent;
import com.paymentplatform.paymentservice.application.service.PaymentService;
import com.paymentplatform.paymentservice.domain.entity.IdempotencyKey;
import com.paymentplatform.paymentservice.domain.entity.OutboxEvent;
import com.paymentplatform.paymentservice.domain.entity.Payment;
import com.paymentplatform.paymentservice.domain.repository.IdempotencyKeyRepository;
import com.paymentplatform.paymentservice.domain.repository.OutboxEventRepository;
import com.paymentplatform.paymentservice.domain.repository.PaymentRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.kafka.listener.auto-startup=false",
                "spring.kafka.admin.fail-fast=false",
                "spring.task.scheduling.enabled=false",
                "management.tracing.enabled=false",
                "management.metrics.export.prometheus.enabled=false"
        }
)
@Testcontainers(disabledWithoutDocker = false)
class PaymentServiceIT {

    private static final AtomicInteger WALLET_DEBIT_CALLS = new AtomicInteger(0);
    private static final com.fasterxml.jackson.databind.ObjectMapper STUB_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private static final HttpServer WALLET_STUB = startWalletStub();

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("payment_db")
                    .withUsername("test")
                    .withPassword("test");

    @Container
    private static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.4"));

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
        registry.add("services.wallet.base-url", () ->
                "http://localhost:" + WALLET_STUB.getAddress().getPort());
    }

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
        idempotencyKeyRepository.deleteAll();
        paymentRepository.deleteAll();
        WALLET_DEBIT_CALLS.set(0);
        if (stringRedisTemplate.getConnectionFactory() != null) {
            try (var connection = stringRedisTemplate.getConnectionFactory().getConnection()) {
                if (connection.serverCommands() != null) {
                    connection.serverCommands().flushDb();
                }
            }
        }
    }

    @AfterAll
    static void tearDownStub() {
        WALLET_STUB.stop(0);
    }

    @Test
    void processPayment_successPath_isIdempotentAndPersistsCompletedState() {
        String orderId = "order-it-success-001";
        InventoryReservedEvent event = inventoryReservedEvent(orderId);

        paymentService.processPayment(event);
        paymentService.processPayment(event);

        List<Payment> paymentsForOrder = paymentRepository.findAll().stream()
                .filter(payment -> orderId.equals(payment.getOrderId()))
                .toList();

        assertThat(paymentsForOrder).hasSize(1);
        Payment payment = paymentsForOrder.get(0);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.getTransactionReference()).startsWith("WALLET-");
        assertThat(stringRedisTemplate.opsForValue().get("payment:lock:" + orderId)).isNull();

        IdempotencyKey idempotency = idempotencyKeyRepository.findByKey("order:" + orderId).orElseThrow();
        assertThat(idempotency.getResponseStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(idempotency.isExpired()).isFalse();

        List<OutboxEvent> completedEvents = outboxEventRepository.findAll().stream()
                .filter(eventRow -> KafkaTopics.PAYMENT_COMPLETED.equals(eventRow.getTopic()))
                .filter(eventRow -> eventRow.getPayload().contains(orderId))
                .toList();
        assertThat(completedEvents).hasSize(1);

        assertThat(WALLET_DEBIT_CALLS.get()).isEqualTo(1);
    }

    @Test
    void processPayment_walletDecline_isIdempotentAndPersistsFailedState() {
        String orderId = "order-it-fail-001";
        InventoryReservedEvent event = inventoryReservedEvent(orderId);

        paymentService.processPayment(event);
        paymentService.processPayment(event);

        List<Payment> paymentsForOrder = paymentRepository.findAll().stream()
                .filter(payment -> orderId.equals(payment.getOrderId()))
                .toList();

        assertThat(paymentsForOrder).hasSize(1);
        Payment payment = paymentsForOrder.get(0);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureCode()).isEqualTo("INSUFFICIENT_WALLET_BALANCE");
        assertThat(payment.getFailureReason()).contains("Wallet balance insufficient");
        assertThat(stringRedisTemplate.opsForValue().get("payment:lock:" + orderId)).isNull();

        IdempotencyKey idempotency = idempotencyKeyRepository.findByKey("order:" + orderId).orElseThrow();
        assertThat(idempotency.getResponseStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(idempotency.isExpired()).isFalse();

        List<OutboxEvent> failedEvents = outboxEventRepository.findAll().stream()
                .filter(eventRow -> KafkaTopics.PAYMENT_FAILED.equals(eventRow.getTopic()))
                .filter(eventRow -> eventRow.getPayload().contains(orderId))
                .toList();
        assertThat(failedEvents).hasSize(1);

        assertThat(WALLET_DEBIT_CALLS.get()).isEqualTo(1);
    }

    @Test
    void processPayment_concurrentRequests_acquiresRedisLockAndExecutesOnce() throws InterruptedException {
        String orderId = "order-it-concurrent-001";
        InventoryReservedEvent event = inventoryReservedEvent(orderId);

        Thread thread1 = new Thread(() -> paymentService.processPayment(event));
        Thread thread2 = new Thread(() -> paymentService.processPayment(event));

        thread1.start();
        thread2.start();
        thread1.join();
        thread2.join();

        List<Payment> paymentsForOrder = paymentRepository.findAll().stream()
                .filter(payment -> orderId.equals(payment.getOrderId()))
                .toList();

        assertThat(paymentsForOrder).hasSize(1);
        assertThat(paymentsForOrder.get(0).getStatus()).isEqualTo(PaymentStatus.COMPLETED);

        // Redis lock should be released after execution
        assertThat(stringRedisTemplate.opsForValue().get("payment:lock:" + orderId)).isNull();

        // Wallet debit should have been called only once despite concurrent requests
        assertThat(WALLET_DEBIT_CALLS.get()).isEqualTo(1);
    }

    private static InventoryReservedEvent inventoryReservedEvent(String orderId) {
        return InventoryReservedEvent.builder()
                .orderId(orderId)
                .reservationId("reservation-" + orderId)
                .customerId("customer-it-001")
                .customerEmail("customer-it@example.com")
                .paymentMethod(PaymentMethod.WALLET)
                .totalAmount(MoneyDto.builder().amount(new BigDecimal("4097.00")).currency("INR").build())
                .reservedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .build();
    }

    private static HttpServer startWalletStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/api/wallets/debit", exchange -> {
                try {
                    if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                        sendJson(exchange, 405, "{\"success\":false,\"message\":\"METHOD_NOT_ALLOWED\"}");
                        return;
                    }

                    String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    String orderId = STUB_MAPPER.readTree(requestBody).path("orderId").asText("");
                    String customerId = STUB_MAPPER.readTree(requestBody).path("customerId").asText("customer-it-001");

                    WALLET_DEBIT_CALLS.incrementAndGet();

                    if (orderId.contains("fail")) {
                        sendJson(exchange, 400, "{\"success\":false,\"message\":\"INSUFFICIENT_WALLET_BALANCE\"}");
                        return;
                    }

                    String response = "{\"success\":true,\"data\":{\"walletId\":\"wallet-it-001\",\"customerId\":\""
                            + customerId
                            + "\",\"balance\":{\"amount\":5000.00,\"currency\":\"INR\"}}}";
                    sendJson(exchange, 200, response);
                } catch (Exception e) {
                    sendJson(exchange, 500, "{\"success\":false,\"message\":\"WALLET_STUB_ERROR\"}");
                } finally {
                    exchange.close();
                }
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new RuntimeException("Failed to start wallet stub server", e);
        }
    }

    private static void sendJson(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
}
