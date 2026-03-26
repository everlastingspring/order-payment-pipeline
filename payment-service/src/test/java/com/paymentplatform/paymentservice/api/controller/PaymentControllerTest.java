package com.paymentplatform.paymentservice.api.controller;

import com.paymentplatform.commonlib.dto.MoneyDto;
import com.paymentplatform.commonlib.dto.PaymentDto;
import com.paymentplatform.commonlib.enums.PaymentMethod;
import com.paymentplatform.commonlib.enums.PaymentStatus;
import com.paymentplatform.commonlib.exception.ResourceNotFoundException;
import com.paymentplatform.paymentservice.application.service.PaymentService;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("PaymentController")
class PaymentControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private PaymentService paymentService;

    private PaymentDto stubDto(UUID paymentId, String orderId) {
        return PaymentDto.builder()
                .paymentId(paymentId.toString())
                .orderId(orderId)
                .customerId("customer-001")
                .status(PaymentStatus.COMPLETED)
                .amount(MoneyDto.builder().amount(new BigDecimal("4097.00")).currency("INR").build())
                .paymentMethod(PaymentMethod.WALLET)
                .build();
    }

    @Nested
    @DisplayName("GET /api/payments/{paymentId}")
    class GetPayment {

        @Test
        @DisplayName("returns 200 with payment DTO")
        void returns200() throws Exception {
            UUID id = UUID.randomUUID();
            when(paymentService.getPayment(id)).thenReturn(stubDto(id, "order-123"));

            mockMvc.perform(get("/api/payments/{paymentId}", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.paymentId").value(id.toString()))
                    .andExpect(jsonPath("$.data.status").value("COMPLETED"));
        }

        @Test
        @DisplayName("returns 404 when payment not found")
        void returns404() throws Exception {
            UUID id = UUID.randomUUID();
            when(paymentService.getPayment(id)).thenThrow(new ResourceNotFoundException("Payment", id));

            mockMvc.perform(get("/api/payments/{paymentId}", id))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }
    }

    @Nested
    @DisplayName("GET /api/payments/order/{orderId}")
    class GetPaymentByOrder {

        @Test
        @DisplayName("returns 200 with payment DTO")
        void returns200() throws Exception {
            UUID payId = UUID.randomUUID();
            String orderId = UUID.randomUUID().toString();
            when(paymentService.getPaymentByOrderId(orderId)).thenReturn(stubDto(payId, orderId));

            mockMvc.perform(get("/api/payments/order/{orderId}", orderId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.orderId").value(orderId));
        }

        @Test
        @DisplayName("returns 404 when order has no payment")
        void returns404() throws Exception {
            String orderId = UUID.randomUUID().toString();
            when(paymentService.getPaymentByOrderId(orderId))
                    .thenThrow(new ResourceNotFoundException("Payment", orderId));

            mockMvc.perform(get("/api/payments/order/{orderId}", orderId))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }
    }

    @Nested
    @DisplayName("GET /api/payments/customer/{customerId}")
    class GetPaymentsByCustomer {

        @Test
        @DisplayName("returns 200 with paged results")
        void returns200WithPaging() throws Exception {
            UUID payId = UUID.randomUUID();
            Page<PaymentDto> page = new PageImpl<>(
                    List.of(stubDto(payId, "order-1")), PageRequest.of(0, 20), 1);
            when(paymentService.getPaymentsByCustomer(eq("customer-001"), any())).thenReturn(page);

            mockMvc.perform(get("/api/payments/customer/customer-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content").isArray())
                    .andExpect(jsonPath("$.data.totalElements").value(1));
        }

        @Test
        @DisplayName("returns 200 with custom page params")
        void respectsPageParams() throws Exception {
            Page<PaymentDto> page = new PageImpl<>(List.of(), PageRequest.of(2, 5), 0);
            when(paymentService.getPaymentsByCustomer(eq("customer-001"), any())).thenReturn(page);

            mockMvc.perform(get("/api/payments/customer/customer-001")
                            .param("page", "2").param("size", "5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.page").value(2))
                    .andExpect(jsonPath("$.data.size").value(5));
        }
    }
}
