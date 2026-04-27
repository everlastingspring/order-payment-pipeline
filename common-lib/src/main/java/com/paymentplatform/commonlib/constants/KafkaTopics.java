package com.paymentplatform.commonlib.constants;

/**
 * Central registry of all Kafka topic names used across the platform.
 *
 * <p>Using constants avoids magic strings in {@code @KafkaListener} annotations,
 * outbox event builders, and tests. All producers and consumers reference these
 * names so a topic rename only requires a single change here.</p>
 *
 * <p>Topic-to-service routing (see CLAUDE.md for the full table):</p>
 * <pre>
 *   order.created      → inventory-service, notification-service
 *   inventory.reserved → order-service, payment-service
 *   inventory.failed   → order-service
 *   payment.completed  → order-service, notification-service
 *   payment.failed     → order-service
 *   order.completed    → notification-service
 *   order.cancelled    → notification-service, inventory-service, wallet-service
 *   order.failed       → inventory-service
 *   notification.dlq   → (DLQ retry-worker — future)
 * </pre>
 */
public final class KafkaTopics {

    /** Utility class — not instantiable. */
    private KafkaTopics() {}

    /** Published by order-service when a new order is persisted. */
    public static final String ORDER_CREATED       = "order.created";

    /** Published by payment-service when a charge succeeds. */
    public static final String PAYMENT_COMPLETED   = "payment.completed";

    /** Published by payment-service when a charge fails. */
    public static final String PAYMENT_FAILED      = "payment.failed";

    /**
     * Published by inventory-service when stock is successfully locked.
     * This is the trigger for payment-service in the sequential saga.
     */
    public static final String INVENTORY_RESERVED  = "inventory.reserved";

    /** Published by inventory-service when stock cannot be reserved. */
    public static final String INVENTORY_FAILED    = "inventory.failed";

    /** Published by order-service when the order reaches CONFIRMED state. */
    public static final String ORDER_COMPLETED     = "order.completed";

    /** Published by order-service when an order is explicitly cancelled. */
    public static final String ORDER_CANCELLED     = "order.cancelled";

    /** Published by order-service when the saga fails (inventory or payment step). */
    public static final String ORDER_FAILED        = "order.failed";

    /**
     * Dead-letter queue for notifications that exhausted retry attempts.
     * Published by notification-service; consumed by a retry-worker (future).
     */
    public static final String NOTIFICATION_DLQ    = "notification.dlq";
}
