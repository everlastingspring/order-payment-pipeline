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

/**
 * REST controller exposing inventory query and admin endpoints.
 *
 * <p>Base path: {@code /api/inventory}</p>
 *
 * <p>Stock reservations are triggered asynchronously via Kafka ({@code order.created} event)
 * and are NOT exposed as HTTP endpoints — the saga drives reservation, not HTTP callers.</p>
 *
 * <p>Admin operations (restock) are available at {@code PATCH /api/inventory/{productId}/restock}.
 * No auth enforcement at the service layer — the API gateway is responsible for JWT validation
 * and role-based access.</p>
 */
@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    /** Application service handling all inventory business logic. */
    private final InventoryService inventoryService;

    // ── Read ──

    /**
     * Returns inventory records for all products.
     * Used as the initial smoke-test to confirm seed data is loaded.
     *
     * @return 200 OK with list of all inventory DTOs
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<InventoryDto>>> getAllInventory() {
        List<InventoryDto> inventory = inventoryService.getAllInventory();
        return ResponseEntity.ok(ApiResponse.<List<InventoryDto>>builder()
                .success(true)
                .message("All inventory retrieved")
                .data(inventory)
                .build());
    }

    /**
     * Returns the inventory record for a specific product.
     *
     * @param productId UUID of the product
     * @return 200 OK with the inventory DTO, or 404 if no inventory row exists
     */
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
