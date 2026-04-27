package com.paymentplatform.commonlib.enums;

/**
 * Computed availability state of an inventory row.
 *
 * <p>Set automatically by {@code Inventory.updateStatus()} after every
 * {@link com.paymentplatform.inventoryservice.domain.entity.Inventory#reserve reserve()},
 * {@link com.paymentplatform.inventoryservice.domain.entity.Inventory#release release()}, or
 * {@link com.paymentplatform.inventoryservice.domain.entity.Inventory#restock restock()} call.</p>
 */
public enum InventoryStatus {

    /** Available stock exists ({@code availableQuantity > 0}). Orders can be placed. */
    AVAILABLE,

    /** Some units are reserved for active orders, but more are available. Currently unused in code — AVAILABLE covers both. */
    RESERVED,

    /** No units available but some are currently reserved for orders ({@code availableQuantity == 0, reservedQuantity > 0}). */
    DEPLETED,

    /** No units available and none reserved ({@code availableQuantity == 0, reservedQuantity == 0}). */
    OUT_OF_STOCK,

    /** Units have been released back to available after a cancellation or saga failure. */
    RELEASED
}
