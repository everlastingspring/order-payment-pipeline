package com.paymentplatform.commonlib.constants;

public final class KafkaTopics {

    private KafkaTopics() {}

    public static final String ORDER_CREATED       = "order.created";
    public static final String PAYMENT_COMPLETED   = "payment.completed";
    public static final String PAYMENT_FAILED      = "payment.failed";
    public static final String INVENTORY_RESERVED  = "inventory.reserved";
    public static final String INVENTORY_FAILED    = "inventory.failed";
    public static final String ORDER_COMPLETED     = "order.completed";
    public static final String ORDER_CANCELLED     = "order.cancelled";
    public static final String ORDER_FAILED        = "order.failed";
    public static final String NOTIFICATION_DLQ    = "notification.dlq";
}
