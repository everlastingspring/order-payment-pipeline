package com.paymentplatform.inventoryservice.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymentplatform.commonlib.dto.InventoryDto;
import com.paymentplatform.commonlib.dto.RestockRequest;
import com.paymentplatform.commonlib.enums.InventoryStatus;
import com.paymentplatform.commonlib.exception.ResourceNotFoundException;
import com.paymentplatform.inventoryservice.application.service.InventoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InventoryController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("InventoryController")
class InventoryControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private InventoryService inventoryService;

    private InventoryDto stubDto(UUID productId, String name, int available) {
        return InventoryDto.builder()
                .productId(productId.toString())
                .productName(name)
                .sku("SKU-001")
                .availableQuantity(available)
                .reservedQuantity(0)
                .status(available > 0 ? InventoryStatus.AVAILABLE : InventoryStatus.OUT_OF_STOCK)
                .build();
    }

    @Nested
    @DisplayName("GET /api/inventory")
    class GetAllInventory {

        @Test
        @DisplayName("returns 200 with all inventory items")
        void returns200() throws Exception {
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            when(inventoryService.getAllInventory())
                    .thenReturn(List.of(stubDto(id1, "Mouse", 100), stubDto(id2, "Keyboard", 50)));

            mockMvc.perform(get("/api/inventory"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(2));
        }
    }

    @Nested
    @DisplayName("GET /api/inventory/{productId}")
    class GetInventory {

        @Test
        @DisplayName("returns 200 with inventory DTO")
        void returns200() throws Exception {
            UUID id = UUID.randomUUID();
            when(inventoryService.getInventory(id)).thenReturn(stubDto(id, "Mouse", 100));

            mockMvc.perform(get("/api/inventory/{productId}", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.productId").value(id.toString()));
        }

        @Test
        @DisplayName("returns 404 when product not found")
        void returns404() throws Exception {
            UUID id = UUID.randomUUID();
            when(inventoryService.getInventory(id)).thenThrow(new ResourceNotFoundException("Inventory", id));

            mockMvc.perform(get("/api/inventory/{productId}", id))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }
    }

    @Nested
    @DisplayName("GET /api/inventory/low-stock")
    class GetLowStock {

        @Test
        @DisplayName("returns 200 with default threshold")
        void returns200DefaultThreshold() throws Exception {
            UUID id = UUID.randomUUID();
            when(inventoryService.getLowStockInventory(10))
                    .thenReturn(List.of(stubDto(id, "Webcam", 5)));

            mockMvc.perform(get("/api/inventory/low-stock"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.length()").value(1));
        }

        @Test
        @DisplayName("returns 200 with custom threshold")
        void returns200CustomThreshold() throws Exception {
            when(inventoryService.getLowStockInventory(5)).thenReturn(List.of());

            mockMvc.perform(get("/api/inventory/low-stock").param("threshold", "5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(0));
        }
    }

    @Nested
    @DisplayName("PATCH /api/inventory/{productId}/restock")
    class Restock {

        @Test
        @DisplayName("returns 200 on successful restock")
        void returns200() throws Exception {
            UUID id = UUID.randomUUID();
            when(inventoryService.restockInventory(eq(id), any()))
                    .thenReturn(stubDto(id, "Mouse", 150));

            RestockRequest request = RestockRequest.builder()
                    .quantity(50).reason("Supplier delivery").build();

            mockMvc.perform(patch("/api/inventory/{productId}/restock", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.availableQuantity").value(150));
        }

        @Test
        @DisplayName("returns 404 when product not found")
        void returns404() throws Exception {
            UUID id = UUID.randomUUID();
            when(inventoryService.restockInventory(eq(id), any()))
                    .thenThrow(new ResourceNotFoundException("Inventory", id));

            RestockRequest request = RestockRequest.builder()
                    .quantity(50).reason("Delivery").build();

            mockMvc.perform(patch("/api/inventory/{productId}/restock", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        @DisplayName("returns 400 when quantity is missing")
        void returns400MissingQuantity() throws Exception {
            UUID id = UUID.randomUUID();

            mockMvc.perform(patch("/api/inventory/{productId}/restock", id)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\": \"test\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }
    }
}
