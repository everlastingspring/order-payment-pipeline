package com.paymentplatform.paymentservice.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentplatform.commonlib.constants.KafkaTopics;
import com.paymentplatform.commonlib.dto.MoneyDto;
import com.paymentplatform.commonlib.dto.PaymentDto;
import com.paymentplatform.commonlib.enums.PaymentStatus;
import com.paymentplatform.commonlib.events.InventoryReservedEvent;
import com.paymentplatform.commonlib.events.PaymentCompletedEvent;
import com.paymentplatform.commonlib.events.PaymentFailedEvent;
import com.paymentplatform.commonlib.exception.ResourceNotFoundException;
import com.paymentplatform.paymentservice.application.mapper.PaymentMapper;
import com.paymentplatform.paymentservice.domain.entity.IdempotencyKey;
import com.paymentplatform.paymentservice.domain.entity.OutboxEvent;
import com.paymentplatform.paymentservice.domain.entity.OutboxStatus;
import com.paymentplatform.paymentservice.domain.entity.Payment;
import com.paymentplatform.paymentservice.domain.repository.IdempotencyKeyRepository;
import com.paymentplatform.paymentservice.domain.repository.OutboxEventRepository;
import com.paymentplatform.paymentservice.domain.repository.PaymentRepository;
import com.paymentplatform.paymentservice.application.processor.PaymentProcessor;
import com.paymentplatform.paymentservice.infrastructure.gateway.GatewayResponse;
import com.paymentplatform.paymentservice.infrastructure.lock.RedisLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Core application service for payment-service.
 *
 * <p><strong>Sequential saga flow</strong> — triggered by {@code inventory.reserved}
 * (NOT {@code order.created}): stock is locked before payment is ever attempted.</p>
 *
 * <pre>
 * 1. Check idempotency_keys (fast path — skip if already processed)
 * 2. Acquire Redis distributed lock on orderId (prevent concurrent processing)
 * 3. Double-check idempotency inside lock (check-then-act is atomic)
 * 4. [Transactional] Create Payment (status=PROCESSING)
 * 5. [Transactional] Route to PaymentProcessor (WALLET → WalletPaymentProcessor)
 * 6. [Transactional] Update Payment (COMPLETED or FAILED)
 * 7. [Transactional] Save outbox event (payment.completed or payment.failed)
 * 8. [Transactional] Save idempotency key (TTL 24 h)
 * 9. Release Redis lock
 * </pre>
 *
 * <p>Steps 4–8 are inside a single {@code @Transactional} method ({@link #executePayment}).
 * The Redis lock (steps 2, 9) wraps the transaction to prevent two instances racing on the same
 * Kafka redelivery.</p>
 *
 * <p><strong>Strategy pattern for processors:</strong> All {@link PaymentProcessor} beans are
 * injected as a list. On each payment, the service finds the first whose {@code supports(method)}
 * returns {@code true}. Currently only {@code WalletPaymentProcessor} is active.</p>
 *
 * <p><strong>What this service does NOT do:</strong></p>
 * <ul>
 *   <li>It does not create orders or consume {@code order.created}.</li>
 *   <li>It does not refund — wallet refunds are handled by wallet-service consuming {@code order.cancelled}.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    /** Repository for payment records. */
    private final PaymentRepository paymentRepository;

    /** Repository for idempotency key records that prevent double-charging. */
    private final IdempotencyKeyRepository idempotencyKeyRepository;

    /** Repository for outbox events — payment.completed / payment.failed. */
    private final OutboxEventRepository outboxEventRepository;

    /** Redis-based distributed lock to prevent concurrent payment processing for the same order. */
    private final RedisLockService redisLockService;

    /** MapStruct mapper for converting {@link Payment} entities to DTOs. */
    private final PaymentMapper paymentMapper;

    /** Jackson mapper for serialising events and DTOs to JSON. */
    private final ObjectMapper objectMapper;

    /**
     * All {@link PaymentProcessor} beans injected by Spring.
     * To add UPI/CARD support: create a new {@code @Component} implementing {@link PaymentProcessor}.
     * No changes needed here.
     */
    @Autowired
    private List<PaymentProcessor> processors;

    /**
     * TTL for idempotency key records, in seconds.
     * Configurable via {@code payment.idempotency.ttl-seconds} (default 86400 = 24 h).
     */
    @Value("${payment.idempotency.ttl-seconds:86400}")
    private long idempotencyTtlSeconds;

    /**
     * Processes a payment triggered by an inventory.reserved event.
     * Sequential saga: inventory must be reserved before payment is attempted.
     * This is the main entry point called by OrderEventConsumer.
     */
    public void processPayment(InventoryReservedEvent event) {
        String orderId = event.getOrderId();
        String idempotencyKeyValue = "order:" + orderId;

        // ── Step 1: Check idempotency (fast path, no lock needed) ──
        var existingKey = idempotencyKeyRepository.findByKey(idempotencyKeyValue);
        if (existingKey.isPresent() && !existingKey.get().isExpired()) {
            log.info("Idempotent duplicate detected — skipping: orderId={}, paymentId={}",
                    orderId, existingKey.get().getPaymentId());
            return;
        }

        // ── Step 2: Acquire distributed lock ──
        String lockToken = redisLockService.tryAcquire(orderId);
        if (lockToken == null) {
            log.warn("Could not acquire lock for orderId={} — another instance is processing", orderId);
            throw new RuntimeException(
                    "Could not acquire lock for orderId=" + orderId + " — another instance is processing");
        }

        try {
            // Double-check idempotency inside the lock
            var recheck = idempotencyKeyRepository.findByKey(idempotencyKeyValue);
            if (recheck.isPresent() && !recheck.get().isExpired()) {
                log.info("Idempotent duplicate detected after lock: orderId={}", orderId);
                return;
            }

            // ── Step 3–7: Process within a transaction ──
            executePayment(event, idempotencyKeyValue);

        } finally {
            // ── Step 8: Always release the lock ──
            redisLockService.release(orderId, lockToken);
        }
    }

    /**
     * The transactional core: creates payment, charges gateway, saves result + outbox event.
     */
    @Transactional
    protected void executePayment(InventoryReservedEvent event, String idempotencyKeyValue) {
        String orderId = event.getOrderId();

        // Create payment record using fields carried from OrderCreatedEvent via inventory.reserved
        Payment payment = Payment.builder()
                .orderId(orderId)
                .customerId(event.getCustomerId())
                .status(PaymentStatus.PROCESSING)
                .amount(event.getTotalAmount().getAmount())
                .currency(event.getTotalAmount().getCurrency())
                .paymentMethod(event.getPaymentMethod())
                .idempotencyKey(idempotencyKeyValue)
                .build();

        // savedPayment is a new effectively-final reference — required for lambda capture below
        final Payment savedPayment = paymentRepository.save(payment);
        log.info("Payment initiated: paymentId={}, orderId={}, method={}",
                savedPayment.getId(), orderId, savedPayment.getPaymentMethod());

        // Route to correct processor (WALLET → WalletPaymentProcessor, future UPI/CARD → GatewayPaymentProcessor)
        PaymentProcessor processor = processors.stream()
                .filter(p -> p.supports(savedPayment.getPaymentMethod()))
                .findFirst()
                .orElseThrow(() -> new UnsupportedOperationException(
                        "No payment processor found for method: " + savedPayment.getPaymentMethod()));

        GatewayResponse response = processor.charge(
                orderId,
                savedPayment.getCustomerId(),
                savedPayment.getAmount(),
                savedPayment.getCurrency()
        );

        if (response.successful()) {
            savedPayment.markCompleted(response.transactionReference());
            paymentRepository.save(savedPayment);

            publishPaymentCompleted(savedPayment, event.getCustomerEmail());
            log.info("Payment completed: paymentId={}, txnRef={}", savedPayment.getId(), response.transactionReference());
        } else {
            savedPayment.markFailed(response.failureReason(), response.failureCode());
            paymentRepository.save(savedPayment);

            publishPaymentFailed(savedPayment, event.getCustomerEmail());
            log.warn("Payment failed: paymentId={}, reason={}", savedPayment.getId(), response.failureReason());
        }

        // Cache the result for idempotency
        saveIdempotencyKey(idempotencyKeyValue, savedPayment);
    }

    // ── Read operations ──

    /**
     * Retrieves a payment by its UUID.
     *
     * @param paymentId UUID of the payment record
     * @return the payment DTO
     * @throws com.paymentplatform.commonlib.exception.ResourceNotFoundException if not found
     */
    @Transactional(readOnly = true)
    public PaymentDto getPayment(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));
        return paymentMapper.toDto(payment);
    }

    /**
     * Retrieves the payment associated with a specific order.
     *
     * @param orderId order identifier (String UUID)
     * @return the payment DTO
     * @throws com.paymentplatform.commonlib.exception.ResourceNotFoundException if not found
     */
    @Transactional(readOnly = true)
    public PaymentDto getPaymentByOrderId(String orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", orderId));
        return paymentMapper.toDto(payment);
    }

    /**
     * Returns paginated payment history for a customer, sorted newest first.
     *
     * @param customerId customer identifier
     * @param pageable   pagination parameters
     * @return page of payment DTOs
     */
    @Transactional(readOnly = true)
    public Page<PaymentDto> getPaymentsByCustomer(String customerId, Pageable pageable) {
        return paymentRepository.findByCustomerIdOrderByCreatedAtDesc(customerId, pageable)
                .map(paymentMapper::toDto);
    }

    // ── Outbox event helpers ──

    /**
     * Builds and saves a {@code payment.completed} outbox event.
     * Carries customer email through to notification-service.
     */
    private void publishPaymentCompleted(Payment payment, String customerEmail) {
        PaymentCompletedEvent event = PaymentCompletedEvent.builder()
                .paymentId(payment.getId().toString())
                .orderId(payment.getOrderId())
                .customerId(payment.getCustomerId())
                .customerEmail(customerEmail)
                .amount(MoneyDto.builder()
                        .amount(payment.getAmount())
                        .currency(payment.getCurrency())
                        .build())
                .paymentMethod(payment.getPaymentMethod())
                .transactionReference(payment.getTransactionReference())
                .completedAt(Instant.now())
                .build();

        saveOutboxEvent(payment.getId().toString(), KafkaTopics.PAYMENT_COMPLETED, event);
    }

    /**
     * Builds and saves a {@code payment.failed} outbox event.
     * Order-service will set the order to FAILED on consuming this event.
     */
    private void publishPaymentFailed(Payment payment, String customerEmail) {
        PaymentFailedEvent event = PaymentFailedEvent.builder()
                .paymentId(payment.getId().toString())
                .orderId(payment.getOrderId())
                .customerId(payment.getCustomerId())
                .customerEmail(customerEmail)
                .attemptedAmount(MoneyDto.builder()
                        .amount(payment.getAmount())
                        .currency(payment.getCurrency())
                        .build())
                .paymentMethod(payment.getPaymentMethod())
                .failureReason(payment.getFailureReason())
                .failureCode(payment.getFailureCode())
                .failedAt(Instant.now())
                .build();

        saveOutboxEvent(payment.getId().toString(), KafkaTopics.PAYMENT_FAILED, event);
    }

    private void saveOutboxEvent(String aggregateId, String topic, Object event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            OutboxEvent outbox = OutboxEvent.builder()
                    .aggregateType("Payment")
                    .aggregateId(aggregateId)
                    .eventType(topic)
                    .topic(topic)
                    .payload(payload)
                    .status(OutboxStatus.PENDING)
                    .build();
            outboxEventRepository.save(outbox);
            log.debug("Outbox event saved: aggregateId={}, topic={}", aggregateId, topic);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize event for outbox", e);
        }
    }

    /**
     * Saves an idempotency key record that caches the payment outcome for {@code idempotencyTtlSeconds}.
     * On Kafka redelivery, {@link #processPayment} checks this table first and skips reprocessing
     * if a non-expired key exists — preventing double-charging the customer.
     *
     * @param key     the idempotency key string ({@code "order:<orderId>"})
     * @param payment the completed or failed payment entity
     */
    private void saveIdempotencyKey(String key, Payment payment) {
        try {
            IdempotencyKey idempotency = IdempotencyKey.builder()
                    .key(key)
                    .paymentId(payment.getId())
                    .responseStatus(payment.getStatus())
                    .responseBody(objectMapper.writeValueAsString(paymentMapper.toDto(payment)))
                    .expiresAt(Instant.now().plusSeconds(idempotencyTtlSeconds))
                    .build();
            idempotencyKeyRepository.save(idempotency);
            log.debug("Idempotency key saved: key={}, paymentId={}", key, payment.getId());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize payment response for idempotency", e);
        }
    }
}
