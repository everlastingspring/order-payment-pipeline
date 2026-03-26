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

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final OrderMapper orderMapper;
    private final ObjectMapper objectMapper;

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

    @Transactional(readOnly = true)
    public OrderDto getOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        return orderMapper.toDto(order);
    }

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
