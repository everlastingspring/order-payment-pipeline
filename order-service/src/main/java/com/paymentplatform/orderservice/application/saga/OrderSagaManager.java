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
 * Handles saga state transitions for the order lifecycle.
 *
 * Parallel choreography: both payment-service and inventory-service
 * react to order.created independently. This manager joins the results:
 *  - Both succeed → CONFIRMED → publish order.completed
 *  - Either fails → CANCELLED → publish order.cancelled (triggers compensation)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderSagaManager {

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    @Transactional
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        Order order = findOrder(event.getOrderId());

        if (order.getStatus() == OrderStatus.CANCELLED) {
            log.warn("Payment completed for already cancelled order: {}", event.getOrderId());
            return;
        }

        order.setPaymentCompleted(true);
        order.setPaymentId(event.getPaymentId());
        order.setSagaStatus(SagaStatus.PAYMENT_STEP_COMPLETED);
        log.info("Payment completed for order: {}", event.getOrderId());

        checkSagaCompletion(order);
    }

    @Transactional
    public void handlePaymentFailed(PaymentFailedEvent event) {
        Order order = findOrder(event.getOrderId());

        if (order.getStatus() == OrderStatus.CANCELLED) {
            log.warn("Payment failed event for already cancelled order: {}", event.getOrderId());
            return;
        }

        order.setSagaStatus(SagaStatus.PAYMENT_STEP_FAILED);
        order.setStatus(OrderStatus.CANCELLED);
        order.setSagaStatus(SagaStatus.COMPENSATING);
        orderRepository.save(order);

        log.info("Payment failed for order: {}, reason: {}", event.getOrderId(), event.getFailureReason());

        orderService.publishOrderCancelled(order,
                "Payment failed: " + event.getFailureReason(), "SAGA_COMPENSATION");
    }

    @Transactional
    public void handleInventoryReserved(InventoryReservedEvent event) {
        Order order = findOrder(event.getOrderId());

        if (order.getStatus() == OrderStatus.CANCELLED) {
            log.warn("Inventory reserved for already cancelled order: {}", event.getOrderId());
            return;
        }

        order.setInventoryReserved(true);
        order.setSagaStatus(SagaStatus.INVENTORY_STEP_COMPLETED);
        log.info("Inventory reserved for order: {}", event.getOrderId());

        checkSagaCompletion(order);
    }

    @Transactional
    public void handleInventoryFailed(InventoryFailedEvent event) {
        Order order = findOrder(event.getOrderId());

        if (order.getStatus() == OrderStatus.CANCELLED) {
            log.warn("Inventory failed event for already cancelled order: {}", event.getOrderId());
            return;
        }

        order.setSagaStatus(SagaStatus.INVENTORY_STEP_FAILED);
        order.setStatus(OrderStatus.CANCELLED);
        order.setSagaStatus(SagaStatus.COMPENSATING);
        orderRepository.save(order);

        log.info("Inventory reservation failed for order: {}, reason: {}", event.getOrderId(), event.getFailureReason());

        orderService.publishOrderCancelled(order,
                "Inventory reservation failed: " + event.getFailureReason(), "SAGA_COMPENSATION");
    }

    private void checkSagaCompletion(Order order) {
        if (order.isPaymentCompleted() && order.isInventoryReserved()) {
            order.setStatus(OrderStatus.CONFIRMED);
            order.setSagaStatus(SagaStatus.COMPLETED);
            orderRepository.save(order);

            log.info("Saga completed — order confirmed: {}", order.getId());
            orderService.publishOrderCompleted(order);
        } else {
            orderRepository.save(order);
        }
    }

    private Order findOrder(String orderId) {
        return orderRepository.findById(UUID.fromString(orderId))
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
    }
}
