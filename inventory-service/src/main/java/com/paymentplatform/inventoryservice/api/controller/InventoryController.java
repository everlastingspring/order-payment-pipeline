package com.paymentplatform.inventoryservice.api.controller;

import com.paymentplatform.commonlib.dto.ApiResponse;
import com.paymentplatform.commonlib.dto.InventoryDto;
import com.paymentplatform.inventoryservice.application.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<InventoryDto>> getInventory(@PathVariable UUID productId) {
        InventoryDto inventory = inventoryService.getInventory(productId);
        return ResponseEntity.ok(ApiResponse.<InventoryDto>builder()
                .success(true)
                .message("Inventory retrieved")
                .data(inventory)
                .build());
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<InventoryDto>>> getAllInventory() {
        List<InventoryDto> inventory = inventoryService.getAllInventory();
        return ResponseEntity.ok(ApiResponse.<List<InventoryDto>>builder()
                .success(true)
                .message("All inventory retrieved")
                .data(inventory)
                .build());
    }
}
