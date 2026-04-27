package com.paymentplatform.orderservice.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentplatform.commonlib.constants.KafkaTopics;
import com.paymentplatform.commonlib.dto.MoneyDto;
import com.paymentplatform.commonlib.dto.OrderDto;
import com.paymentplatform.commonlib.enums.OrderStatus;
import com.paymentplatform.commonlib.enums.SagaStatus;
import com.paymentplatform.commonlib.events.OrderCreatedEvent;
import com.paymentplatform.commonlib.events.OrderFailedEvent;
import com.paymentplatform.commonlib.exception.InvalidOrderStateException;
import com.paymentplatform.commonlib.exception.ResourceNotFoundException;
import com.paymentplatform.orderservice.api.dto.CreateOrderRequest;
import com.paymentplatform.orderservice.application.mapper.OrderMapper;
import com.paymentplatform.orderservice.domain.entity.Order;
import com.paymentplatform.orderservice.domain.entity.OrderItem;
import com.paymentplatform.orderservice.domain.entity.OutboxEvent;
import com.paymentplatform.orderservice.domain.entity.OutboxStatus;
import com.paymentplatform.orderservice.domain.repository.OrderRepository;
import com.paymentplatform.orderservice.domain.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Application service for order lifecycle management.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Creating orders and saving the {@code order.created} outbox event.</li>
 *   <li>Cancelling orders with correct refund logic depending on previous status.</li>
 *   <li>Publishing outbox events for {@code order.cancelled}, {@code order.failed},
 *       and {@code order.completed} on behalf of the saga manager.</li>
 * </ul>
 *
 * <p>Does NOT advance the order status in response to Kafka events — that is
 * {@link com.paymentplatform.orderservice.application.saga.OrderSagaManager}'s job.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    /** Persistence for {@link com.paymentplatform.orderservice.domain.entity.Order} aggregate roots. */
    private final OrderRepository orderRepository;

    /** Persistence for outbox rows — written in the same transaction as the business change. */
    private final OutboxEventRepository outboxEventRepository;

    /** Converts between {@link com.paymentplatform.orderservice.domain.entity.Order} and {@code OrderDto}. */
    private final OrderMapper orderMapper;

    /** Used to serialise domain events to JSON for the outbox payload column. */
    private final ObjectMapper objectMapper;

    /**
     * Creates a new order, computes line-item totals, and queues an {@code order.created}
     * outbox event in the same transaction.
     *
     * @param request validated order creation payload
     * @return the persisted order as a DTO
     */
    @Transactional
    public OrderDto createOrder(CreateOrderRequest request) {
        Order order = Order.builder()
                .customerId(request.getCustomerId())
                .customerEmail(request.getCustomerEmail())
                .status(OrderStatus.PENDING)
                .sagaStatus(SagaStatus.STARTED)
                .paymentMethod(request.getPaymentMethod())
                .shippingAddress(request.getShippingAddress())
                .currency(request.getItems().get(0).getCurrency())
                .paymentCompleted(false)
                .inventoryReserved(false)
                .build();

        BigDecimal totalAmount = BigDecimal.ZERO;
        for (CreateOrderRequest.OrderItemRequest itemReq : request.getItems()) {
            BigDecimal subtotal = itemReq.getUnitPrice().multiply(BigDecimal.valueOf(itemReq.getQuantity()));
            totalAmount = totalAmount.add(subtotal);

            OrderItem item = OrderItem.builder()
                    .productId(itemReq.getProductId())
                    .productName(itemReq.getProductName())
                    .sku(itemReq.getSku())
                    .quantity(itemReq.getQuantity())
                    .unitPrice(itemReq.getUnitPrice())
                    .currency(itemReq.getCurrency())
                    .subtotal(subtotal)
                    .build();
            order.addItem(item);
        }
        order.setTotalAmount(totalAmount);

        Order saved = orderRepository.save(order);
        log.info("Order created: id={}, customerId={}", saved.getId(), saved.getCustomerId());

        // Build the domain event
        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .orderId(saved.getId().toString())
                .customerId(saved.getCustomerId())
                .customerEmail(saved.getCustomerEmail())
                .totalAmount(MoneyDto.builder()
                        .amount(saved.getTotalAmount())
                        .currency(saved.getCurrency())
                        .build())
                .paymentMethod(saved.getPaymentMethod())
                .items(orderMapper.toDto(saved).getItems())
                .shippingAddress(saved.getShippingAddress())
                .createdAt(saved.getCreatedAt())
                .build();

        saveOutboxEvent(saved.getId().toString(), KafkaTopics.ORDER_CREATED, event);

        return orderMapper.toDto(saved);
    }

    /**
     * Retrieves a single order by its primary key.
     *
     * @param orderId UUID of the order
     * @return the order DTO
     * @throws com.paymentplatform.commonlib.exception.ResourceNotFoundException if not found
     */
    @Transactional(readOnly = true)
    public OrderDto getOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        return orderMapper.toDto(order);
    }

    /**
     * Returns a page of orders for the given customer, sorted newest first.
     *
     * @param customerId the customer identifier
     * @param pageable   paging and sorting parameters
     * @return paginated order DTOs
     */
    @Transactional(readOnly = true)
    public Page<OrderDto> getOrdersByCustomer(String customerId, Pageable pageable) {
        return orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId, pageable)
                .map(orderMapper::toDto);
    }

    @Transactional
    public OrderDto cancelOrder(UUID orderId, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        // CANCELLED and FAILED are terminal — nothing left to cancel.
        // PENDING, INVENTORY_RESERVED, CONFIRMED are all cancellable:
        //   - PENDING / INVENTORY_RESERVED: no payment taken yet → no wallet refund
        //   - CONFIRMED: payment already debited → wallet-service will issue a full refund
        if (order.getStatus() == OrderStatus.CANCELLED
                || order.getStatus() == OrderStatus.FAILED) {
            throw new InvalidOrderStateException(
                    orderId.toString(), order.getStatus(), OrderStatus.CANCELLED);
        }

        // Capture the current status BEFORE mutation — used as previousStatus in the event
        OrderStatus previousStatus = order.getStatus();
        boolean willRefund = order.isPaymentCompleted();

        order.setStatus(OrderStatus.CANCELLED);
        order.setSagaStatus(SagaStatus.COMPENSATING);
        Order saved = orderRepository.save(order);

        publishOrderCancelled(saved, reason, "CUSTOMER", previousStatus);

        log.info("Order cancelled: orderId={}, previousStatus={}, willRefund={}",
                orderId, previousStatus, willRefund);

        return orderMapper.toDto(saved);
    }

    /**
     * Serialises an event object to JSON and saves it as a PENDING outbox row.
     * Must be called within an active transaction so the outbox write is atomic
     * with the business change that triggered the event.
     *
     * @param aggregateId the order UUID string — used as the Kafka message key
     * @param topic       the Kafka topic name
     * @param event       the domain event object to serialise
     */
    private void saveOutboxEvent(String aggregateId, String topic, Object event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            OutboxEvent outbox = OutboxEvent.builder()
                    .aggregateType("Order")
                    .aggregateId(aggregateId)
                    .eventType(topic)
                    .topic(topic)
                    .payload(payload)
                    .status(OutboxStatus.PENDING)
                    .build();
            outboxEventRepository.save(outbox);
            log.debug("Outbox event saved: aggregateId={}, topic={}", aggregateId, topic);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize event for outbox", e);
        }
    }

    /**
     * Publishes order.cancelled to the outbox.
     *
     * @param previousStatus the order status BEFORE it was set to CANCELLED — caller must capture
     *                       this before mutating order.status, otherwise it always reads CANCELLED.
     */
    public void publishOrderCancelled(Order order, String reason, String cancelledBy,
                                      OrderStatus previousStatus) {
        var event = com.paymentplatform.commonlib.events.OrderCancelledEvent.builder()
                .orderId(order.getId().toString())
                .customerId(order.getCustomerId())
                .customerEmail(order.getCustomerEmail())
                .cancellationReason(reason)
                .cancelledBy(cancelledBy)
                .previousStatus(previousStatus)
                .refundAmount(order.isPaymentCompleted()
                        ? MoneyDto.builder().amount(order.getTotalAmount()).currency(order.getCurrency()).build()
                        : MoneyDto.builder().amount(BigDecimal.ZERO).currency(order.getCurrency()).build())
                .build();
        saveOutboxEvent(order.getId().toString(), KafkaTopics.ORDER_CANCELLED, event);
    }

    /**
     * Saves an {@code order.failed} outbox event.
     * Called by {@code OrderSagaManager} when inventory or payment steps fail.
     *
     * @param order         the failed order
     * @param failureReason human-readable failure description
     * @param failedStep    "INVENTORY" or "PAYMENT" — tells inventory-service whether to release stock
     */
    public void publishOrderFailed(Order order, String failureReason, String failedStep) {
        var event = OrderFailedEvent.builder()
                .orderId(order.getId().toString())
                .customerId(order.getCustomerId())
                .customerEmail(order.getCustomerEmail())
                .failureReason(failureReason)
                .failedStep(failedStep)
                .build();
        saveOutboxEvent(order.getId().toString(), KafkaTopics.ORDER_FAILED, event);
    }

    /**
     * Saves an {@code order.completed} outbox event.
     * Called by {@code OrderSagaManager} when the order is CONFIRMED.
     *
     * @param order the confirmed order
     */
    public void publishOrderCompleted(Order order) {
        var event = com.paymentplatform.commonlib.events.OrderCompletedEvent.builder()
                .orderId(order.getId().toString())
                .customerId(order.getCustomerId())
                .customerEmail(order.getCustomerEmail())
                .totalAmount(MoneyDto.builder()
                        .amount(order.getTotalAmount())
                        .currency(order.getCurrency())
                        .build())
                .build();
        saveOutboxEvent(order.getId().toString(), KafkaTopics.ORDER_COMPLETED, event);
    }
}
