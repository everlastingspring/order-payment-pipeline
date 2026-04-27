package com.paymentplatform.commonlib.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.paymentplatform.commonlib.enums.PaymentMethod;
import com.paymentplatform.commonlib.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * API transfer object representing a payment record.
 *
 * <p>Returned by payment query endpoints ({@code GET /api/payments/{id}},
 * {@code GET /api/payments/order/{orderId}}, {@code GET /api/payments/customer/{customerId}}).</p>
 *
 * <p>Payments are never created via REST — they are created internally by payment-service
 * when it processes an {@code inventory.reserved} Kafka event.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentDto {

    /** UUID of the payment record. */
    private String paymentId;

    /** The order this payment is for. */
    private String orderId;

    /** Customer who was charged. */
    private String customerId;

    /** Current payment status: PROCESSING → COMPLETED or FAILED. */
    private PaymentStatus status;

    /** Amount charged (or attempted). */
    private MoneyDto amount;

    /** Payment method used (currently always WALLET). */
    private PaymentMethod paymentMethod;

    /** Idempotency key used to prevent double-charging — format {@code "order:<orderId>"}. */
    private String idempotencyKey;

    /**
     * Provider-assigned transaction reference for a successful payment.
     * Format depends on the processor: {@code "WALLET-<12-char-UUID>"} for wallet payments.
     * Null for failed payments.
     */
    private String transactionReference;

    /** Human-readable failure reason; null for successful payments. */
    private String failureReason;

    /** When the payment record was created (at PROCESSING state). */
    private Instant createdAt;

    /** When the payment was last updated (status transition timestamp). */
    private Instant updatedAt;
}
