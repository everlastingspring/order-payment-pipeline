package com.paymentplatform.paymentservice.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.paymentplatform.commonlib.dto.MoneyDto;
import com.paymentplatform.commonlib.dto.PaymentDto;
import com.paymentplatform.commonlib.enums.PaymentMethod;
import com.paymentplatform.commonlib.enums.PaymentStatus;
import com.paymentplatform.commonlib.events.InventoryReservedEvent;
import com.paymentplatform.commonlib.exception.ResourceNotFoundException;
import com.paymentplatform.paymentservice.application.mapper.PaymentMapper;
import com.paymentplatform.paymentservice.application.processor.PaymentProcessor;
import com.paymentplatform.paymentservice.domain.entity.IdempotencyKey;
import com.paymentplatform.paymentservice.domain.entity.OutboxEvent;
import com.paymentplatform.paymentservice.domain.entity.Payment;
import com.paymentplatform.paymentservice.domain.repository.IdempotencyKeyRepository;
import com.paymentplatform.paymentservice.domain.repository.OutboxEventRepository;
import com.paymentplatform.paymentservice.domain.repository.PaymentRepository;
import com.paymentplatform.paymentservice.infrastructure.gateway.GatewayResponse;
import com.paymentplatform.paymentservice.infrastructure.lock.RedisLockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService")
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private IdempotencyKeyRepository idempotencyKeyRepository;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private RedisLockService redisLockService;
    @Mock private PaymentMapper paymentMapper;
    @Mock private PaymentProcessor walletProcessor;

    private ObjectMapper objectMapper;
    private PaymentService paymentService;

    private static final String ORDER_ID    = UUID.randomUUID().toString();
    private static final String CUSTOMER_ID = "customer-001";
    private static final String CUSTOMER_EMAIL = "customer-001@example.com";
    private static final String LOCK_TOKEN  = "lock-token-xyz";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        paymentService = new PaymentService(
                paymentRepository, idempotencyKeyRepository, outboxEventRepository,
                redisLockService, paymentMapper, objectMapper);
        // @Autowired + @Value fields — not in constructor so set via reflection
        ReflectionTestUtils.setField(paymentService, "processors", List.of(walletProcessor));
        ReflectionTestUtils.setField(paymentService, "idempotencyTtlSeconds", 86400L);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private InventoryReservedEvent buildEvent() {
        return InventoryReservedEvent.builder()
                .orderId(ORDER_ID)
                .customerId(CUSTOMER_ID)
                .customerEmail(CUSTOMER_EMAIL)
                .totalAmount(MoneyDto.builder().amount(new BigDecimal("4097.00")).currency("INR").build())
                .paymentMethod(PaymentMethod.WALLET)
                .build();
    }

    /** Stubs paymentRepository.save() to set id/timestamps (normally done by @PrePersist). */
    private void stubPaymentSave() {
        when(paymentRepository.save(any())).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            if (p.getId() == null) {
                setField(p, "id", UUID.randomUUID());
            }
            setField(p, "createdAt", Instant.now());
            setField(p, "updatedAt", Instant.now());
            return p;
        });
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

    private void stubForFullExecution() {
        when(idempotencyKeyRepository.findByKey(anyString())).thenReturn(Optional.empty());
        when(redisLockService.tryAcquire(ORDER_ID)).thenReturn(LOCK_TOKEN);
        when(walletProcessor.supports(PaymentMethod.WALLET)).thenReturn(true);
        stubPaymentSave();
        when(paymentMapper.toDto(any()))
                .thenReturn(PaymentDto.builder().status(PaymentStatus.COMPLETED).build());
    }

    // ── getPayment ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getPayment")
    class GetPayment {

        @Test
        @DisplayName("returns mapped DTO when payment found by ID")
        void found_returnsMappedDto() {
            UUID paymentId = UUID.randomUUID();
            Payment payment = Payment.builder().orderId(ORDER_ID).customerId(CUSTOMER_ID).build();
            setField(payment, "id", paymentId);
            PaymentDto dto = PaymentDto.builder().build();

            when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
            when(paymentMapper.toDto(payment)).thenReturn(dto);

            assertThat(paymentService.getPayment(paymentId)).isEqualTo(dto);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when payment not found")
        void notFound_throwsResourceNotFoundException() {
            UUID paymentId = UUID.randomUUID();
            when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.getPayment(paymentId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── getPaymentByOrderId ───────────────────────────────────────────────────

    @Nested
    @DisplayName("getPaymentByOrderId")
    class GetPaymentByOrderId {

        @Test
        @DisplayName("returns mapped DTO when payment found by orderId")
        void found_returnsMappedDto() {
            Payment payment = Payment.builder().orderId(ORDER_ID).customerId(CUSTOMER_ID).build();
            PaymentDto dto = PaymentDto.builder().build();

            when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(payment));
            when(paymentMapper.toDto(payment)).thenReturn(dto);

            assertThat(paymentService.getPaymentByOrderId(ORDER_ID)).isEqualTo(dto);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when no payment for orderId")
        void notFound_throwsResourceNotFoundException() {
            when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.getPaymentByOrderId(ORDER_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── getPaymentsByCustomer ─────────────────────────────────────────────────

    @Nested
    @DisplayName("getPaymentsByCustomer")
    class GetPaymentsByCustomer {

        @Test
        @DisplayName("returns paged and mapped results")
        void returnsMappedPage() {
            Pageable pageable = PageRequest.of(0, 10);
            Payment payment = Payment.builder().orderId(ORDER_ID).customerId(CUSTOMER_ID).build();
            PaymentDto dto = PaymentDto.builder().build();
            Page<Payment> page = new PageImpl<>(List.of(payment));

            when(paymentRepository.findByCustomerIdOrderByCreatedAtDesc(CUSTOMER_ID, pageable))
                    .thenReturn(page);
            when(paymentMapper.toDto(payment)).thenReturn(dto);

            Page<PaymentDto> result = paymentService.getPaymentsByCustomer(CUSTOMER_ID, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0)).isEqualTo(dto);
        }

        @Test
        @DisplayName("returns empty page when customer has no payments")
        void noPayments_returnsEmptyPage() {
            Pageable pageable = PageRequest.of(0, 10);
            when(paymentRepository.findByCustomerIdOrderByCreatedAtDesc("unknown", pageable))
                    .thenReturn(Page.empty());

            Page<PaymentDto> result = paymentService.getPaymentsByCustomer("unknown", pageable);

            assertThat(result.getContent()).isEmpty();
        }
    }

    // ── processPayment — idempotency & locking ────────────────────────────────

    @Nested
    @DisplayName("processPayment — idempotency and locking")
    class ProcessPaymentIdempotency {

        @Test
        @DisplayName("skips processing immediately when valid (non-expired) idempotency key exists")
        void validKey_skipsProcessing_noLockAcquired() {
            IdempotencyKey validKey = IdempotencyKey.builder()
                    .key("order:" + ORDER_ID)
                    .paymentId(UUID.randomUUID())
                    .responseStatus(PaymentStatus.COMPLETED)
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();
            when(idempotencyKeyRepository.findByKey("order:" + ORDER_ID))
                    .thenReturn(Optional.of(validKey));

            paymentService.processPayment(buildEvent());

            verify(redisLockService, never()).tryAcquire(anyString());
            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("proceeds with payment when key exists but is expired")
        void expiredKey_proceedsWithPayment() {
            IdempotencyKey expiredKey = IdempotencyKey.builder()
                    .key("order:" + ORDER_ID)
                    .paymentId(UUID.randomUUID())
                    .responseStatus(PaymentStatus.COMPLETED)
                    .expiresAt(Instant.now().minusSeconds(3600))  // expired
                    .build();
            when(idempotencyKeyRepository.findByKey("order:" + ORDER_ID))
                    .thenReturn(Optional.of(expiredKey))           // first check — expired
                    .thenReturn(Optional.empty());                  // double-check after lock
            when(redisLockService.tryAcquire(ORDER_ID)).thenReturn(LOCK_TOKEN);
            when(walletProcessor.supports(PaymentMethod.WALLET)).thenReturn(true);
            when(walletProcessor.charge(anyString(), anyString(), any(), anyString()))
                    .thenReturn(GatewayResponse.success("WALLET-TX-001"));
            stubPaymentSave();
            when(paymentMapper.toDto(any())).thenReturn(PaymentDto.builder().status(PaymentStatus.COMPLETED).build());

            paymentService.processPayment(buildEvent());

            // Lock was acquired → payment was processed
            verify(redisLockService).tryAcquire(ORDER_ID);
            verify(paymentRepository, atLeastOnce()).save(any());
        }

        @Test
        @DisplayName("throws RuntimeException when Redis lock cannot be acquired")
        void lockNotAcquired_throwsRuntimeException() {
            when(idempotencyKeyRepository.findByKey(anyString())).thenReturn(Optional.empty());
            when(redisLockService.tryAcquire(ORDER_ID)).thenReturn(null);

            assertThatThrownBy(() -> paymentService.processPayment(buildEvent()))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining(ORDER_ID);

            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("skips processing on double-check inside lock when concurrent request already completed")
        void doubleCheckInsideLock_skipsWhenKeyAppearsAfterLock() {
            IdempotencyKey key = IdempotencyKey.builder()
                    .key("order:" + ORDER_ID)
                    .paymentId(UUID.randomUUID())
                    .responseStatus(PaymentStatus.COMPLETED)
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();
            when(idempotencyKeyRepository.findByKey("order:" + ORDER_ID))
                    .thenReturn(Optional.empty())       // first check — no key
                    .thenReturn(Optional.of(key));      // inside lock — key now present
            when(redisLockService.tryAcquire(ORDER_ID)).thenReturn(LOCK_TOKEN);

            paymentService.processPayment(buildEvent());

            verify(paymentRepository, never()).save(any());
            verify(redisLockService).release(ORDER_ID, LOCK_TOKEN);
        }

        @Test
        @DisplayName("lock is always released in finally block even when processing throws")
        void lockAlwaysReleasedOnException() {
            when(idempotencyKeyRepository.findByKey(anyString())).thenReturn(Optional.empty());
            when(redisLockService.tryAcquire(ORDER_ID)).thenReturn(LOCK_TOKEN);
            when(walletProcessor.supports(PaymentMethod.WALLET)).thenReturn(false); // no processor → throws
            stubPaymentSave();

            assertThatThrownBy(() -> paymentService.processPayment(buildEvent()))
                    .isInstanceOf(UnsupportedOperationException.class);

            verify(redisLockService).release(ORDER_ID, LOCK_TOKEN);
        }
    }

    // ── executePayment — payment outcomes ─────────────────────────────────────

    @Nested
    @DisplayName("executePayment — payment outcomes")
    class ExecutePayment {

        @Test
        @DisplayName("creates payment with PROCESSING status before charging gateway")
        void firstSave_isProcessingStatus() {
            // Snapshot the status at each save() call — ArgumentCaptor captures references
            // which are mutated in-place, so we record the status at invocation time
            java.util.List<PaymentStatus> statusesAtSaveTime = new java.util.ArrayList<>();

            when(idempotencyKeyRepository.findByKey(anyString())).thenReturn(Optional.empty());
            when(redisLockService.tryAcquire(ORDER_ID)).thenReturn(LOCK_TOKEN);
            when(walletProcessor.supports(PaymentMethod.WALLET)).thenReturn(true);
            when(paymentMapper.toDto(any()))
                    .thenReturn(PaymentDto.builder().status(PaymentStatus.COMPLETED).build());

            // Custom save stub that records status at invocation time
            when(paymentRepository.save(any())).thenAnswer(inv -> {
                Payment p = inv.getArgument(0);
                statusesAtSaveTime.add(p.getStatus());
                if (p.getId() == null) {
                    setField(p, "id", UUID.randomUUID());
                }
                setField(p, "createdAt", Instant.now());
                setField(p, "updatedAt", Instant.now());
                return p;
            });

            when(walletProcessor.charge(anyString(), anyString(), any(), anyString()))
                    .thenReturn(GatewayResponse.success("WALLET-TX-001"));

            paymentService.processPayment(buildEvent());

            assertThat(statusesAtSaveTime).hasSizeGreaterThanOrEqualTo(2);
            assertThat(statusesAtSaveTime.get(0)).isEqualTo(PaymentStatus.PROCESSING);

            ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository, atLeast(1)).save(captor.capture());
            Payment lastSave = captor.getAllValues().get(captor.getAllValues().size() - 1);
            assertThat(lastSave.getOrderId()).isEqualTo(ORDER_ID);
            assertThat(lastSave.getCustomerId()).isEqualTo(CUSTOMER_ID);
            assertThat(lastSave.getPaymentMethod()).isEqualTo(PaymentMethod.WALLET);
        }

        @Test
        @DisplayName("successful payment: saves COMPLETED status, outbox payment.completed, idempotency key")
        void successfulPayment_savesCompletedStatusAndOutboxAndIdempotencyKey() {
            stubForFullExecution();
            when(walletProcessor.charge(anyString(), anyString(), any(), anyString()))
                    .thenReturn(GatewayResponse.success("WALLET-TX-001"));

            paymentService.processPayment(buildEvent());

            ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository, times(2)).save(paymentCaptor.capture());
            Payment finalSave = paymentCaptor.getAllValues().get(1);
            assertThat(finalSave.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
            assertThat(finalSave.getTransactionReference()).isEqualTo("WALLET-TX-001");

            ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxEventRepository, atLeastOnce()).save(outboxCaptor.capture());
            assertThat(outboxCaptor.getAllValues()).anyMatch(e -> "payment.completed".equals(e.getTopic()));

            verify(idempotencyKeyRepository).save(any(IdempotencyKey.class));
        }

        @Test
        @DisplayName("failed payment: saves FAILED status with failureCode, outbox payment.failed")
        void failedPayment_savesFailedStatusAndOutbox() {
            stubForFullExecution();
            when(walletProcessor.charge(anyString(), anyString(), any(), anyString()))
                    .thenReturn(GatewayResponse.failure("INSUFFICIENT_WALLET_BALANCE", "Not enough funds"));

            paymentService.processPayment(buildEvent());

            ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
            verify(paymentRepository, times(2)).save(captor.capture());
            Payment finalSave = captor.getAllValues().get(1);
            assertThat(finalSave.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(finalSave.getFailureCode()).isEqualTo("INSUFFICIENT_WALLET_BALANCE");
            assertThat(finalSave.getFailureReason()).isEqualTo("Not enough funds");

            ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxEventRepository, atLeastOnce()).save(outboxCaptor.capture());
            assertThat(outboxCaptor.getAllValues()).anyMatch(e -> "payment.failed".equals(e.getTopic()));
        }

        @Test
        @DisplayName("no matching processor throws UnsupportedOperationException mentioning the method")
        void noMatchingProcessor_throwsUnsupportedOperationException() {
            when(idempotencyKeyRepository.findByKey(anyString())).thenReturn(Optional.empty());
            when(redisLockService.tryAcquire(ORDER_ID)).thenReturn(LOCK_TOKEN);
            when(walletProcessor.supports(PaymentMethod.WALLET)).thenReturn(false);
            stubPaymentSave();

            assertThatThrownBy(() -> paymentService.processPayment(buildEvent()))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("WALLET");
        }

        @Test
        @DisplayName("processor is called with correct orderId, customerId, amount, currency")
        void processorCalledWithCorrectArguments() {
            stubForFullExecution();
            when(walletProcessor.charge(anyString(), anyString(), any(), anyString()))
                    .thenReturn(GatewayResponse.success("WALLET-TX-001"));

            paymentService.processPayment(buildEvent());

            verify(walletProcessor).charge(ORDER_ID, CUSTOMER_ID, new BigDecimal("4097.00"), "INR");
        }

        @Test
        @DisplayName("outbox event payload contains orderId and payment details")
        void outboxPayload_containsOrderId() {
            stubForFullExecution();
            when(walletProcessor.charge(anyString(), anyString(), any(), anyString()))
                    .thenReturn(GatewayResponse.success("WALLET-TX-001"));

            paymentService.processPayment(buildEvent());

            ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxEventRepository, atLeastOnce()).save(captor.capture());
            OutboxEvent completedEvent = captor.getAllValues().stream()
                    .filter(e -> "payment.completed".equals(e.getTopic()))
                    .findFirst()
                    .orElseThrow();

            assertThat(completedEvent.getPayload()).contains(ORDER_ID);
            assertThat(completedEvent.getPayload()).contains(CUSTOMER_EMAIL);
            assertThat(completedEvent.getAggregateType()).isEqualTo("Payment");
        }

        @Test
        @DisplayName("throws RuntimeException when outbox event serialization fails")
        void serializationFailure_throwsRuntimeException() throws JsonProcessingException {
            ObjectMapper spyMapper = spy(objectMapper);
            doThrow(new JsonProcessingException("fail") {}).when(spyMapper).writeValueAsString(any());

            PaymentService serviceWithSpyMapper = new PaymentService(
                    paymentRepository, idempotencyKeyRepository, outboxEventRepository,
                    redisLockService, paymentMapper, spyMapper);
            ReflectionTestUtils.setField(serviceWithSpyMapper, "processors", List.of(walletProcessor));
            ReflectionTestUtils.setField(serviceWithSpyMapper, "idempotencyTtlSeconds", 86400L);

            when(walletProcessor.supports(PaymentMethod.WALLET)).thenReturn(true);
            when(walletProcessor.charge(anyString(), anyString(), any(), anyString()))
                    .thenReturn(GatewayResponse.success("WALLET-TX-001"));
            stubPaymentSave();

            assertThatThrownBy(() -> serviceWithSpyMapper.executePayment(buildEvent(), "order:" + ORDER_ID))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to serialize");
        }

        @Test
        @DisplayName("idempotency key is saved with correct key value and payment reference")
        void idempotencyKey_savedWithCorrectValues() {
            stubForFullExecution();
            when(walletProcessor.charge(anyString(), anyString(), any(), anyString()))
                    .thenReturn(GatewayResponse.success("WALLET-TX-001"));

            paymentService.processPayment(buildEvent());

            ArgumentCaptor<IdempotencyKey> captor = ArgumentCaptor.forClass(IdempotencyKey.class);
            verify(idempotencyKeyRepository).save(captor.capture());
            IdempotencyKey saved = captor.getValue();

            assertThat(saved.getKey()).isEqualTo("order:" + ORDER_ID);
            assertThat(saved.getPaymentId()).isNotNull();
            assertThat(saved.getExpiresAt()).isAfter(Instant.now());
        }
    }
}
