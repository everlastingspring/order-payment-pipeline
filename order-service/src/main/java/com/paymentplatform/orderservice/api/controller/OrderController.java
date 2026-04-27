package com.paymentplatform.orderservice.api.controller;

import com.paymentplatform.commonlib.dto.ApiResponse;
import com.paymentplatform.commonlib.dto.OrderDto;
import com.paymentplatform.commonlib.dto.PagedResponse;
import com.paymentplatform.orderservice.api.dto.CreateOrderRequest;
import com.paymentplatform.orderservice.application.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller exposing the order lifecycle API.
 *
 * <p>All endpoints return {@link com.paymentplatform.commonlib.dto.ApiResponse} wrappers.
 * Authentication is enforced by the API gateway — downstream services trust the
 * {@code X-User-Id} header injected by the gateway.</p>
 *
 * <p>Base path: {@code /api/orders}</p>
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * Creates a new order and starts the inventory reservation saga.
     *
     * @param request validated order payload with customer details and line items
     * @return 201 Created with the new order DTO
     */
    @PostMapping
    public ResponseEntity<ApiResponse<OrderDto>> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        OrderDto order = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(order, "Order created successfully"));
    }

    /**
     * Retrieves a single order by ID. Used for polling saga progress.
     * Status progression: {@code PENDING → INVENTORY_RESERVED → CONFIRMED}.
     *
     * @param orderId UUID of the order
     * @return 200 OK with the order DTO, or 404 if not found
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderDto>> getOrder(@PathVariable UUID orderId) {
        OrderDto order = orderService.getOrder(orderId);
        return ResponseEntity.ok(ApiResponse.ok(order));
    }

    /**
     * Returns a paginated list of orders for a customer, sorted newest first.
     *
     * @param customerId customer identifier
     * @param page       zero-based page number (default 0)
     * @param size       page size (default 20)
     * @return 200 OK with paged order DTOs
     */
    @GetMapping("/customer/{customerId}")
    public ResponseEntity<ApiResponse<PagedResponse<OrderDto>>> getOrdersByCustomer(
            @PathVariable String customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<OrderDto> orders = orderService.getOrdersByCustomer(customerId, PageRequest.of(page, size));

        PagedResponse<OrderDto> pagedResponse = PagedResponse.<OrderDto>builder()
                .content(orders.getContent())
                .page(orders.getNumber())
                .size(orders.getSize())
                .totalElements(orders.getTotalElements())
                .totalPages(orders.getTotalPages())
                .first(orders.isFirst())
                .last(orders.isLast())
                .build();

        return ResponseEntity.ok(ApiResponse.ok(pagedResponse));
    }

    /**
     * Cancels an order. Allowed for PENDING, INVENTORY_RESERVED, and CONFIRMED orders.
     * CONFIRMED cancellations trigger a full wallet refund via {@code order.cancelled}.
     *
     * @param orderId the order to cancel
     * @param reason  human-readable cancellation reason (default: "Cancelled by customer")
     * @return 200 OK with the updated order DTO, or 409 if already terminal
     */
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<ApiResponse<OrderDto>> cancelOrder(
            @PathVariable UUID orderId,
            @RequestParam(defaultValue = "Cancelled by customer") String reason) {
        OrderDto order = orderService.cancelOrder(orderId, reason);
        return ResponseEntity.ok(ApiResponse.ok(order, "Order cancelled"));
    }
}
