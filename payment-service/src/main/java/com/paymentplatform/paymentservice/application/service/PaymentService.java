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
 * Core payment processing service.
 *
 * Sequential saga flow — triggered by inventory.reserved (NOT order.created):
 *   1. Acquire Redis distributed lock on orderId (prevents concurrent processing)
 *   2. Check idempotency_keys table (prevents double charge on redelivery)
 *   3. Create Payment entity (status=PROCESSING)
 *   4. Call gateway simulator (charge)
 *   5. Update Payment (COMPLETED or FAILED)
 *   6. Save outbox event (payment.completed or payment.failed)
 *   7. Save idempotency key (caches the result for 24h)
 *   8. Release Redis lock
 *
 * Steps 3–7 are in a single @Transactional — all-or-nothing in the DB.
 * The Redis lock (steps 1, 8) wraps the transaction to prevent races.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final RedisLockService redisLockService;
    private final PaymentMapper paymentMapper;
    private final ObjectMapper objectMapper;

    /**
     * All PaymentProcessor beans injected by Spring.
     * To add UPI/CARD: create a new @Component implementing PaymentProcessor.
     * No changes needed here.
     */
    @Autowired
    private List<PaymentProcessor> processors;

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

    @Transactional(readOnly = true)
    public PaymentDto getPayment(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));
        return paymentMapper.toDto(payment);
    }

    @Transactional(readOnly = true)
    public PaymentDto getPaymentByOrderId(String orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", orderId));
        return paymentMapper.toDto(payment);
    }

    @Transactional(readOnly = true)
    public Page<PaymentDto> getPaymentsByCustomer(String customerId, Pageable pageable) {
        return paymentRepository.findByCustomerIdOrderByCreatedAtDesc(customerId, pageable)
                .map(paymentMapper::toDto);
    }

    // ── Outbox event helpers ──

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
