package com.paymentplatform.paymentservice.api.controller;

import com.paymentplatform.commonlib.dto.ApiResponse;
import com.paymentplatform.commonlib.dto.PagedResponse;
import com.paymentplatform.commonlib.dto.PaymentDto;
import com.paymentplatform.paymentservice.application.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller exposing payment query endpoints.
 *
 * <p>Payments are created automatically by the saga (triggered by {@code inventory.reserved}).
 * There is no {@code POST /payments} endpoint — payment initiation is event-driven, not HTTP.</p>
 *
 * <p>Base path: {@code /api/payments}</p>
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * Retrieves a payment by its UUID.
     *
     * @param paymentId UUID of the payment record
     * @return 200 OK with the payment DTO, or 404 if not found
     */
    @GetMapping("/{paymentId}")
    public ResponseEntity<ApiResponse<PaymentDto>> getPayment(@PathVariable UUID paymentId) {
        PaymentDto payment = paymentService.getPayment(paymentId);
        return ResponseEntity.ok(ApiResponse.<PaymentDto>builder()
                .success(true)
                .message("Payment retrieved")
                .data(payment)
                .build());
    }

    /**
     * Retrieves the payment associated with a specific order.
     * Useful for the Postman smoke test to confirm the saga completed.
     *
     * @param orderId UUID of the order (as String)
     * @return 200 OK with the payment DTO, or 404 if not found
     */
    @GetMapping("/order/{orderId}")
    public ResponseEntity<ApiResponse<PaymentDto>> getPaymentByOrder(@PathVariable String orderId) {
        PaymentDto payment = paymentService.getPaymentByOrderId(orderId);
        return ResponseEntity.ok(ApiResponse.<PaymentDto>builder()
                .success(true)
                .message("Payment retrieved")
                .data(payment)
                .build());
    }

    /**
     * Returns a paginated payment history for a customer, sorted newest first.
     *
     * @param customerId customer identifier
     * @param page       zero-based page number (default 0)
     * @param size       page size (default 20)
     * @return 200 OK with paged payment DTOs
     */
    @GetMapping("/customer/{customerId}")
    public ResponseEntity<ApiResponse<PagedResponse<PaymentDto>>> getPaymentsByCustomer(
            @PathVariable String customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<PaymentDto> payments = paymentService.getPaymentsByCustomer(customerId, pageable);

        PagedResponse<PaymentDto> pagedResponse = PagedResponse.<PaymentDto>builder()
                .content(payments.getContent())
                .page(payments.getNumber())
                .size(payments.getSize())
                .totalElements(payments.getTotalElements())
                .totalPages(payments.getTotalPages())
                .last(payments.isLast())
                .build();

        return ResponseEntity.ok(ApiResponse.<PagedResponse<PaymentDto>>builder()
                .success(true)
                .message("Payments retrieved")
                .data(pagedResponse)
                .build());
    }
}
