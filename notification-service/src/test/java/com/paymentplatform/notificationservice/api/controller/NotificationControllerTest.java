package com.paymentplatform.notificationservice.api.controller;

import com.paymentplatform.commonlib.dto.NotificationDto;
import com.paymentplatform.commonlib.enums.NotificationStatus;
import com.paymentplatform.commonlib.enums.NotificationType;
import com.paymentplatform.commonlib.exception.ResourceNotFoundException;
import com.paymentplatform.notificationservice.application.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("NotificationController")
class NotificationControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private NotificationService notificationService;

    private NotificationDto stubDto(String notifId) {
        return NotificationDto.builder()
                .notificationId(notifId)
                .customerId("customer-001")
                .recipientEmail("test@example.com")
                .type(NotificationType.ORDER_CONFIRMATION)
                .status(NotificationStatus.SENT)
                .subject("Order Created")
                .attemptCount(1)
                .build();
    }

    @Nested
    @DisplayName("GET /api/notifications/{notificationId}")
    class GetNotification {

        @Test
        @DisplayName("returns 200 with notification DTO")
        void returns200() throws Exception {
            UUID id = UUID.randomUUID();
            when(notificationService.getNotification(id)).thenReturn(stubDto(id.toString()));

            mockMvc.perform(get("/api/notifications/{notificationId}", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.notificationId").value(id.toString()));
        }

        @Test
        @DisplayName("returns 404 when notification not found")
        void returns404() throws Exception {
            UUID id = UUID.randomUUID();
            when(notificationService.getNotification(id))
                    .thenThrow(new ResourceNotFoundException("Notification", id));

            mockMvc.perform(get("/api/notifications/{notificationId}", id))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }
    }

    @Nested
    @DisplayName("GET /api/notifications/customer/{customerId}")
    class GetByCustomer {

        @Test
        @DisplayName("returns 200 with paged results")
        void returns200() throws Exception {
            Page<NotificationDto> page = new PageImpl<>(
                    List.of(stubDto(UUID.randomUUID().toString())),
                    PageRequest.of(0, 20), 1);
            when(notificationService.getNotificationsByCustomer(eq("customer-001"), any()))
                    .thenReturn(page);

            mockMvc.perform(get("/api/notifications/customer/customer-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content").isArray())
                    .andExpect(jsonPath("$.data.totalElements").value(1));
        }

        @Test
        @DisplayName("returns 200 with custom page params")
        void respectsPageParams() throws Exception {
            Page<NotificationDto> page = new PageImpl<>(List.of(), PageRequest.of(1, 10), 0);
            when(notificationService.getNotificationsByCustomer(eq("customer-001"), any()))
                    .thenReturn(page);

            mockMvc.perform(get("/api/notifications/customer/customer-001")
                            .param("page", "1").param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.page").value(1))
                    .andExpect(jsonPath("$.data.size").value(10));
        }

        @Test
        @DisplayName("returns 200 with empty page when customer has no notifications")
        void emptyPage() throws Exception {
            when(notificationService.getNotificationsByCustomer(eq("unknown"), any()))
                    .thenReturn(Page.empty(PageRequest.of(0, 20)));

            mockMvc.perform(get("/api/notifications/customer/unknown"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").isArray())
                    .andExpect(jsonPath("$.data.totalElements").value(0));
        }
    }
}
