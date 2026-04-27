package com.paymentplatform.orderservice.application.saga;

import com.paymentplatform.commonlib.enums.OrderStatus;
import com.paymentplatform.commonlib.enums.SagaStatus;
import com.paymentplatform.commonlib.events.*;
import com.paymentplatform.commonlib.exception.ResourceNotFoundException;
import com.paymentplatform.orderservice.application.service.OrderService;
import com.paymentplatform.orderservice.domain.entity.Order;
import com.paymentplatform.orderservice.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Choreography saga coordinator for the order lifecycle in order-service.
 *
 * <p><strong>Happy path:</strong></p>
 * <pre>
 *   order.created (published by OrderService)
 *       → inventory.reserved (inventory-service)
 *           → [handleInventoryReserved] order → INVENTORY_RESERVED
 *       → payment.completed (payment-service)
 *           → [handlePaymentCompleted] order → CONFIRMED → order.completed published
 * </pre>
 *
 * <p><strong>Failure paths:</strong></p>
 * <pre>
 *   inventory.failed
 *       → [handleInventoryFailed] order → FAILED → order.failed published (failedStep=INVENTORY)
 *         (no stock was reserved, so inventory-service ignores the order.failed)
 *
 *   payment.failed
 *       → [handlePaymentFailed] order → FAILED → order.failed published (failedStep=PAYMENT)
 *         (inventory-service consumes order.failed, releases reserved stock)
 * </pre>
 *
 * <p><strong>Idempotency:</strong> All handlers guard against processing events for orders already
 * in a final state (CONFIRMED, COMPLETED, CANCELLED, FAILED) via {@link #isFinalState(OrderStatus)}.
 * This is necessary because Kafka delivers at-least-once and retries can replay events.</p>
 *
 * <p>This class handles saga state transitions only. All outbox event publishing is delegated
 * to {@link OrderService} to keep the saga manager focused on state machine logic.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderSagaManager {

    /** Direct DB access for loading orders by UUID (no service layer in saga reads). */
    private final OrderRepository orderRepository;

    /** Delegated to for outbox event publishing ({@code publishOrderCompleted}, {@code publishOrderFailed}). */
    private final OrderService orderService;

    /**
     * Step 1 complete: inventory reserved.
     * Update order status to INVENTORY_RESERVED — payment-service will now process the payment.
     */
    @Transactional
    public void handleInventoryReserved(InventoryReservedEvent event) {
        Order order = findOrder(event.getOrderId());

        if (isFinalState(order.getStatus())) {
            log.warn("inventory.reserved received for order already in final state {}: {}",
                    order.getStatus(), event.getOrderId());
            return;
        }

        order.setInventoryReserved(true);
        order.setStatus(OrderStatus.INVENTORY_RESERVED);
        order.setSagaStatus(SagaStatus.INVENTORY_STEP_COMPLETED);
        orderRepository.save(order);

        log.info("Inventory reserved for order: {} — awaiting payment", event.getOrderId());
    }

    /**
     * Step 2 complete: payment succeeded.
     * Mark order CONFIRMED and publish order.completed.
     * No parallel join needed — payment fires only after inventory.reserved.
     */
    @Transactional
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        Order order = findOrder(event.getOrderId());

        if (isFinalState(order.getStatus())) {
            log.warn("payment.completed received for order already in final state {}: {}",
                    order.getStatus(), event.getOrderId());
            return;
        }

        order.setPaymentCompleted(true);
        order.setPaymentId(event.getPaymentId());
        order.setStatus(OrderStatus.CONFIRMED);
        order.setSagaStatus(SagaStatus.COMPLETED);
        orderRepository.save(order);

        log.info("Saga completed — order confirmed: {}", event.getOrderId());
        orderService.publishOrderCompleted(order);
    }

    /**
     * Inventory failed — out of stock or product not found.
     * No payment was attempted. Mark FAILED and publish order.failed.
     */
    @Transactional
    public void handleInventoryFailed(InventoryFailedEvent event) {
        Order order = findOrder(event.getOrderId());

        if (isFinalState(order.getStatus())) {
            log.warn("inventory.failed received for order already in final state {}: {}",
                    order.getStatus(), event.getOrderId());
            return;
        }

        order.setStatus(OrderStatus.FAILED);
        order.setSagaStatus(SagaStatus.FAILED);
        orderRepository.save(order);

        log.info("Inventory reservation failed for order: {}, reason: {}",
                event.getOrderId(), event.getFailureReason());

        orderService.publishOrderFailed(order,
                "Inventory unavailable: " + event.getFailureReason(), "INVENTORY");
    }

    /**
     * Payment failed — gateway declined or error.
     * Inventory was already reserved — publish order.failed so inventory-service can release it.
     */
    @Transactional
    public void handlePaymentFailed(PaymentFailedEvent event) {
        Order order = findOrder(event.getOrderId());

        if (isFinalState(order.getStatus())) {
            log.warn("payment.failed received for order already in final state {}: {}",
                    order.getStatus(), event.getOrderId());
            return;
        }

        order.setStatus(OrderStatus.FAILED);
        order.setSagaStatus(SagaStatus.FAILED);
        orderRepository.save(order);

        log.info("Payment failed for order: {}, reason: {}",
                event.getOrderId(), event.getFailureReason());

        // Inventory WAS reserved — publish order.failed so inventory-service releases it
        orderService.publishOrderFailed(order,
                "Payment declined: " + event.getFailureReason(), "PAYMENT");
    }

    /**
     * Returns {@code true} if the order is in a terminal state where no further saga transitions
     * should occur. Guards all handlers against processing late/replayed Kafka events.
     *
     * @param status the current order status
     * @return {@code true} for CONFIRMED, COMPLETED, CANCELLED, FAILED
     */
    private boolean isFinalState(OrderStatus status) {
        return status == OrderStatus.CONFIRMED
                || status == OrderStatus.COMPLETED
                || status == OrderStatus.CANCELLED
                || status == OrderStatus.FAILED;
    }

    /**
     * Loads an order by its string UUID, throwing {@code ResourceNotFoundException} if absent.
     *
     * @param orderId the order UUID as a string (as carried in Kafka event payloads)
     * @return the {@link Order} entity
     */
    private Order findOrder(String orderId) {
        return orderRepository.findById(UUID.fromString(orderId))
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
    }
}
