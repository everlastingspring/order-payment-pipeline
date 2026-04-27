package com.paymentplatform.commonlib.enums;

/**
 * Categories of customer notifications sent by notification-service.
 *
 * <p>Each type is associated with a Kafka topic trigger and a delivery channel.
 * {@code EmailNotificationChannel} handles all types.
 * {@code SmsNotificationChannel} handles only the high-priority payment types.</p>
 */
public enum NotificationType {

    /** Sent immediately after order.created — "Your order has been received." */
    ORDER_CONFIRMATION,

    /** Sent after payment.completed — "Your payment was successful." Triggers email + SMS. */
    PAYMENT_SUCCESS,

    /** Sent after payment.failed — "Your payment failed, please retry." Triggers email + SMS. */
    PAYMENT_FAILURE,

    /** Reserved for future fulfilment integration — not currently triggered. */
    ORDER_SHIPPED,

    /** Sent after order.cancelled — "Your order has been cancelled." */
    ORDER_CANCELLED,

    /** Sent after order.failed — "Your order could not be completed." */
    ORDER_FAILED,

    /** Reserved for future wallet top-up confirmation — not currently triggered. */
    WALLET_CREDITED,

    /** Reserved for future wallet debit notification — not currently triggered. */
    WALLET_DEBITED
}
