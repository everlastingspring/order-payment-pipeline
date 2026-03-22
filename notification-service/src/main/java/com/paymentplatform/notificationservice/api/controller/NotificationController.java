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

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/{notificationId}")
    public ResponseEntity<ApiResponse<NotificationDto>> getNotification(@PathVariable UUID notificationId) {
        NotificationDto notification = notificationService.getNotification(notificationId);
        return ResponseEntity.ok(ApiResponse.<NotificationDto>builder()
                .success(true)
                .message("Notification retrieved")
                .data(notification)
                .build());
    }

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
