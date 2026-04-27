package com.paymentplatform.commonlib.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing a single line item in an order.
 *
 * <p>Used in {@code OrderCreatedEvent.items} so that inventory-service
 * knows which products and quantities to reserve.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderItemDto {

    /** UUID of the product — matches {@code products.id} in inventory_db. */
    @NotBlank
    private String productId;

    /** Display name (e.g. "Wireless Mouse") — informational, not used for logic. */
    private String productName;

    /** Stock-keeping unit code (e.g. "MOUSE-WL-001"). */
    private String sku;

    /** Number of units ordered — must be at least 1. */
    @Min(1)
    private int quantity;

    /** Price per single unit with currency. */
    @NotNull
    private MoneyDto unitPrice;

    /** Pre-computed {@code unitPrice × quantity} — calculated at creation time. Omitted if null. */
    private MoneyDto subtotal;
}
