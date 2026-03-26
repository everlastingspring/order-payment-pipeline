package com.paymentplatform.orderservice.api.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentplatform.orderservice.api.controller.OrderController;
import com.paymentplatform.orderservice.api.dto.CreateOrderRequest;
import com.paymentplatform.orderservice.application.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("GlobalExceptionHandler — order-service")
class GlobalExceptionHandlerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private OrderService orderService;

    @Test
    @DisplayName("400 — validation errors include field-level details")
    void validationError_returns400WithFieldErrors() throws Exception {
        // Empty body — all @NotBlank/@NotNull fields will fail
        CreateOrderRequest request = CreateOrderRequest.builder().build();

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.validationErrors").isMap())
                .andExpect(jsonPath("$.validationErrors.customerId").exists())
                .andExpect(jsonPath("$.validationErrors.paymentMethod").exists())
                .andExpect(jsonPath("$.validationErrors.items").exists());
    }

    @Test
    @DisplayName("500 — unexpected exception returns generic error")
    void unexpectedException_returns500() throws Exception {
        UUID id = UUID.randomUUID();
        when(orderService.getOrder(id)).thenThrow(new RuntimeException("DB connection lost"));

        mockMvc.perform(get("/api/orders/{orderId}", id))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }
}
