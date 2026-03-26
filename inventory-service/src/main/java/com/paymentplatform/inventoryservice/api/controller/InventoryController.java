package com.paymentplatform.inventoryservice.api.controller;

import com.paymentplatform.commonlib.dto.ApiResponse;
import com.paymentplatform.commonlib.dto.InventoryDto;
import com.paymentplatform.commonlib.dto.RestockRequest;
import com.paymentplatform.inventoryservice.application.service.InventoryService;
import jakarta.validation.Valid;
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

    // ── Read ──

    @GetMapping
    public ResponseEntity<ApiResponse<List<InventoryDto>>> getAllInventory() {
        List<InventoryDto> inventory = inventoryService.getAllInventory();
        return ResponseEntity.ok(ApiResponse.<List<InventoryDto>>builder()
                .success(true)
                .message("All inventory retrieved")
                .data(inventory)
                .build());
    }

    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<InventoryDto>> getInventory(
            @PathVariable("productId") UUID productId) {
        InventoryDto inventory = inventoryService.getInventory(productId);
        return ResponseEntity.ok(ApiResponse.<InventoryDto>builder()
                .success(true)
                .message("Inventory retrieved")
                .data(inventory)
                .build());
    }

    /**
     * Returns products at or below the given stock threshold.
     * Default threshold = 10.
     *
     * GET /api/inventory/low-stock
     * GET /api/inventory/low-stock?threshold=5
     */
    @GetMapping("/low-stock")
    public ResponseEntity<ApiResponse<List<InventoryDto>>> getLowStock(
            @RequestParam(defaultValue = "10") int threshold) {
        List<InventoryDto> lowStock = inventoryService.getLowStockInventory(threshold);
        return ResponseEntity.ok(ApiResponse.<List<InventoryDto>>builder()
                .success(true)
                .message("Low stock products retrieved (threshold=" + threshold + ")")
                .data(lowStock)
                .build());
    }

    // ── Write (admin) ──

    /**
     * Adds stock to a product. Restores status from OUT_OF_STOCK → AVAILABLE.
     *
     * PATCH /api/inventory/{productId}/restock
     * Body: { "quantity": 50, "reason": "Supplier delivery PO-1234" }
     */
    @PatchMapping("/{productId}/restock")
    public ResponseEntity<ApiResponse<InventoryDto>> restock(
            @PathVariable("productId") UUID productId,
            @Valid @RequestBody RestockRequest request) {
        InventoryDto inventory = inventoryService.restockInventory(productId, request);
        return ResponseEntity.ok(ApiResponse.<InventoryDto>builder()
                .success(true)
                .message("Inventory restocked: +" + request.getQuantity() + " units added")
                .data(inventory)
                .build());
    }
}
