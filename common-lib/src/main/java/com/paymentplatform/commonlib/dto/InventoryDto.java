package com.paymentplatform.commonlib.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.paymentplatform.commonlib.enums.InventoryStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * API transfer object representing the stock level of a product in a warehouse.
 *
 * <p>Returned by {@code GET /api/inventory} and {@code GET /api/inventory/{productId}}.
 * Combines product metadata (name, SKU) with live stock quantities for display without
 * requiring a separate product catalogue lookup.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InventoryDto {

    /** UUID of the product. */
    private String productId;

    /** Display name of the product (e.g. "Wireless Mouse"). */
    private String productName;

    /** Unique stock-keeping unit code (e.g. "MOUSE-WL-001"). */
    private String sku;

    /** Units available for new orders (not yet reserved). */
    private int availableQuantity;

    /** Units currently locked for in-flight orders. */
    private int reservedQuantity;

    /** Derived availability status (AVAILABLE, DEPLETED, OUT_OF_STOCK). */
    private InventoryStatus status;

    /** Warehouse identifier (default "WH-001" in single-warehouse mode). */
    private String warehouseId;

    /** Timestamp of the most recent stock quantity change. */
    private Instant lastUpdatedAt;
}
