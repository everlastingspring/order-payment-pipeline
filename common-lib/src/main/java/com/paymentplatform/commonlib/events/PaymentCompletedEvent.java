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
 * Published to {@code payment.completed} after a successful charge.
 *
 * <p>Consumers:</p>
 * <ul>
 *   <li><b>order-service</b>: advances the order from {@code INVENTORY_RESERVED}
 *       to {@code CONFIRMED} and publishes {@code order.completed}.</li>
 *   <li><b>notification-service</b>: sends a "Payment Successful" email/SMS.</li>
 * </ul>
 *
 * <p>Note: wallet debit has already occurred synchronously BEFORE this event is
 * published — the event is informational, not a trigger for the debit.</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentCompletedEvent extends BaseEvent {

    /** UUID of the payment record in payment_db. */
    private String paymentId;

    /** The order this payment is for. */
    private String orderId;

    /** Customer who was charged. */
    private String customerId;

    /** Customer email for receipt notification. */
    private String customerEmail;

    /** Exact amount charged with currency. */
    private MoneyDto amount;

    /** Method used for the charge (WALLET, UPI, CARD, etc.). */
    private PaymentMethod paymentMethod;

    /**
     * Opaque reference from the payment processor.
     * For WALLET payments: "WALLET-XXXXXXXXXXXX".
     * For gateway payments: gateway-assigned transaction ID.
     */
    private String transactionReference;

    /** Timestamp when the charge was confirmed. */
    private Instant completedAt;

    public PaymentCompletedEvent(String paymentId, String orderId, String customerId,
                                 MoneyDto amount, PaymentMethod paymentMethod,
                                 String transactionReference) {
        super(KafkaTopics.PAYMENT_COMPLETED);
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.customerId = customerId;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.transactionReference = transactionReference;
        this.completedAt = Instant.now();
    }
}
