package com.paymentplatform.commonlib.events;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.paymentplatform.commonlib.constants.KafkaTopics;
import com.paymentplatform.commonlib.dto.MoneyDto;
import com.paymentplatform.commonlib.enums.PaymentMethod;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * Published to {@code payment.failed} when a charge attempt is declined or errors.
 *
 * <p>Consumers:</p>
 * <ul>
 *   <li><b>order-service</b>: marks the order {@code FAILED} and publishes
 *       {@code order.failed} so inventory-service releases the reserved stock.</li>
 *   <li><b>notification-service</b>: sends a "Payment Failed — please retry" email/SMS.</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentFailedEvent extends BaseEvent {

    /** UUID of the failed payment record in payment_db. */
    private String paymentId;

    /** The order whose payment failed. */
    private String orderId;

    /** Customer whose wallet/card was declined. */
    private String customerId;

    /** Customer email for failure notification. */
    private String customerEmail;

    /** Amount that was attempted (not charged — the customer owes nothing). */
    private MoneyDto attemptedAmount;

    /** Payment method that was attempted. */
    private PaymentMethod paymentMethod;

    /** Human-readable failure message (e.g. "Wallet balance insufficient"). */
    private String failureReason;

    /**
     * Machine-readable failure code from the processor.
     * Examples: "INSUFFICIENT_WALLET_BALANCE", "GATEWAY_UNAVAILABLE", "INSUFFICIENT_FUNDS".
     */
    private String failureCode;

    /** Timestamp when the failure was recorded. */
    private Instant failedAt;

    public PaymentFailedEvent(String paymentId, String orderId, String customerId,
                              MoneyDto attemptedAmount, PaymentMethod paymentMethod,
                              String failureReason, String failureCode) {
        super(KafkaTopics.PAYMENT_FAILED);
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.customerId = customerId;
        this.attemptedAmount = attemptedAmount;
        this.paymentMethod = paymentMethod;
        this.failureReason = failureReason;
        this.failureCode = failureCode;
        this.failedAt = Instant.now();
    }
}
