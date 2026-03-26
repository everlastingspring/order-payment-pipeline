package com.paymentplatform.orderservice.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentplatform.commonlib.dto.MoneyDto;
import com.paymentplatform.commonlib.dto.OrderDto;
import com.paymentplatform.commonlib.enums.OrderStatus;
import com.paymentplatform.commonlib.enums.PaymentMethod;
import com.paymentplatform.commonlib.exception.InvalidOrderStateException;
import com.paymentplatform.commonlib.exception.ResourceNotFoundException;
import com.paymentplatform.orderservice.api.dto.CreateOrderRequest;
import com.paymentplatform.orderservice.application.service.OrderService;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("OrderController")
class OrderControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private OrderService orderService;

    private OrderDto stubDto(UUID id) {
        return OrderDto.builder()
                .orderId(id.toString())
                .customerId("customer-001")
                .status(OrderStatus.PENDING)
                .totalAmount(MoneyDto.builder().amount(new BigDecimal("4097.00")).currency("INR").build())
                .items(List.of())
                .build();
    }

    @Nested
    @DisplayName("POST /api/orders")
    class CreateOrder {

        @Test
        @DisplayName("returns 201 with order DTO on success")
        void returns201() throws Exception {
            UUID id = UUID.randomUUID();
            when(orderService.createOrder(any())).thenReturn(stubDto(id));

            CreateOrderRequest request = CreateOrderRequest.builder()
                    .customerId("customer-001")
                    .customerEmail("test@example.com")
                    .paymentMethod(PaymentMethod.WALLET)
                    .shippingAddress("123 Test St")
                    .items(List.of(CreateOrderRequest.OrderItemRequest.builder()
                            .productId("a1b2c3d4-0000-0000-0000-000000000001")
                            .productName("Mouse").sku("M-001").quantity(1)
                            .unitPrice(new BigDecimal("799.00")).currency("INR").build()))
                    .build();

            mockMvc.perform(post("/api/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.orderId").value(id.toString()))
                    .andExpect(jsonPath("$.data.status").value("PENDING"));
        }
    }

    @Nested
    @DisplayName("GET /api/orders/{orderId}")
    class GetOrder {

        @Test
        @DisplayName("returns 200 with order DTO")
        void returns200() throws Exception {
            UUID id = UUID.randomUUID();
            when(orderService.getOrder(id)).thenReturn(stubDto(id));

            mockMvc.perform(get("/api/orders/{orderId}", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.orderId").value(id.toString()));
        }

        @Test
        @DisplayName("returns 404 when order not found")
        void returns404() throws Exception {
            UUID id = UUID.randomUUID();
            when(orderService.getOrder(id)).thenThrow(new ResourceNotFoundException("Order", id));

            mockMvc.perform(get("/api/orders/{orderId}", id))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }
    }

    @Nested
    @DisplayName("GET /api/orders/customer/{customerId}")
    class GetOrdersByCustomer {

        @Test
        @DisplayName("returns 200 with paged results")
        void returns200WithPaging() throws Exception {
            UUID id = UUID.randomUUID();
            Page<OrderDto> page = new PageImpl<>(List.of(stubDto(id)), PageRequest.of(0, 20), 1);
            when(orderService.getOrdersByCustomer(eq("customer-001"), any())).thenReturn(page);

            mockMvc.perform(get("/api/orders/customer/customer-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content").isArray())
                    .andExpect(jsonPath("$.data.totalElements").value(1));
        }
    }

    @Nested
    @DisplayName("POST /api/orders/{orderId}/cancel")
    class CancelOrder {

        @Test
        @DisplayName("returns 200 on successful cancel")
        void returns200() throws Exception {
            UUID id = UUID.randomUUID();
            OrderDto dto = stubDto(id);
            dto.setStatus(OrderStatus.CANCELLED);
            when(orderService.cancelOrder(eq(id), any())).thenReturn(dto);

            mockMvc.perform(post("/api/orders/{orderId}/cancel", id)
                            .param("reason", "Test cancel"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        }

        @Test
        @DisplayName("returns 409 when order in terminal state")
        void returns409() throws Exception {
            UUID id = UUID.randomUUID();
            when(orderService.cancelOrder(eq(id), any()))
                    .thenThrow(new InvalidOrderStateException(id.toString(), OrderStatus.FAILED, OrderStatus.CANCELLED));

            mockMvc.perform(post("/api/orders/{orderId}/cancel", id))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.status").value(409));
        }
    }
}
