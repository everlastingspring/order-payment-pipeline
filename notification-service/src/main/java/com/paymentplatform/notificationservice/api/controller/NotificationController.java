package com.paymentplatform.notificationservice.api.controller;

import com.paymentplatform.commonlib.dto.ApiResponse;
import com.paymentplatform.commonlib.dto.NotificationDto;
import com.paymentplatform.commonlib.dto.PagedResponse;
import com.paymentplatform.notificationservice.application.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller exposing notification query endpoints.
 *
 * <p>Base path: {@code /api/notifications}</p>
 *
 * <p>Notifications are created automatically by consuming Kafka events — there is no
 * {@code POST /notifications} endpoint. This controller is read-only, intended for:
 * support tooling, the Postman smoke test, and customer notification history views.</p>
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    /** Application service handling notification queries. */
    private final NotificationService notificationService;

    /**
     * Retrieves a single notification by its UUID.
     *
     * @param notificationId UUID of the notification record
     * @return 200 OK with the notification DTO, or 404 if not found
     */
    @GetMapping("/{notificationId}")
    public ResponseEntity<ApiResponse<NotificationDto>> getNotification(@PathVariable UUID notificationId) {
        NotificationDto notification = notificationService.getNotification(notificationId);
        return ResponseEntity.ok(ApiResponse.<NotificationDto>builder()
                .success(true)
                .message("Notification retrieved")
                .data(notification)
                .build());
    }

    /**
     * Returns paginated notification history for a customer, sorted newest first.
     *
     * @param customerId the customer whose notifications to retrieve
     * @param page       zero-based page number (default 0)
     * @param size       page size (default 20)
     * @return 200 OK with paged notification DTOs
     */
    @GetMapping("/customer/{customerId}")
    public ResponseEntity<ApiResponse<PagedResponse<NotificationDto>>> getByCustomer(
            @PathVariable String customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<NotificationDto> notifications = notificationService.getNotificationsByCustomer(customerId, pageable);

        PagedResponse<NotificationDto> pagedResponse = PagedResponse.<NotificationDto>builder()
                .content(notifications.getContent())
                .page(notifications.getNumber())
                .size(notifications.getSize())
                .totalElements(notifications.getTotalElements())
                .totalPages(notifications.getTotalPages())
                .last(notifications.isLast())
                .build();

        return ResponseEntity.ok(ApiResponse.<PagedResponse<NotificationDto>>builder()
                .success(true)
                .message("Notifications retrieved")
                .data(pagedResponse)
                .build());
    }
}
