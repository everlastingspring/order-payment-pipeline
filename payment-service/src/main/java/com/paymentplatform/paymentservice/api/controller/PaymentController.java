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

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping("/{paymentId}")
    public ResponseEntity<ApiResponse<PaymentDto>> getPayment(@PathVariable UUID paymentId) {
        PaymentDto payment = paymentService.getPayment(paymentId);
        return ResponseEntity.ok(ApiResponse.<PaymentDto>builder()
                .success(true)
                .message("Payment retrieved")
                .data(payment)
                .build());
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<ApiResponse<PaymentDto>> getPaymentByOrder(@PathVariable String orderId) {
        PaymentDto payment = paymentService.getPaymentByOrderId(orderId);
        return ResponseEntity.ok(ApiResponse.<PaymentDto>builder()
                .success(true)
                .message("Payment retrieved")
                .data(payment)
                .build());
    }

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
