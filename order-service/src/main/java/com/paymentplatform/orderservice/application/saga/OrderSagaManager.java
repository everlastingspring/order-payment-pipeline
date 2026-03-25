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
 * Sequential choreography saga for the order lifecycle.
 *
 * Step 1: order.created    → inventory-service reserves stock → inventory.reserved
 * Step 2: inventory.reserved → payment-service charges       → payment.completed / payment.failed
 * Step 3: payment.completed → this manager → order CONFIRMED  → order.completed
 *
 * Failure paths:
 *   inventory.failed  → order FAILED (no payment was attempted)
 *   payment.failed    → order FAILED → order.failed (inventory-service releases stock)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderSagaManager {

    private final OrderRepository orderRepository;
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

    private boolean isFinalState(OrderStatus status) {
        return status == OrderStatus.CONFIRMED
                || status == OrderStatus.COMPLETED
                || status == OrderStatus.CANCELLED
                || status == OrderStatus.FAILED;
    }

    private Order findOrder(String orderId) {
        return orderRepository.findById(UUID.fromString(orderId))
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
    }
}
