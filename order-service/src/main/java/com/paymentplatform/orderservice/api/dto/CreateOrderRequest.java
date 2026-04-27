package com.paymentplatform.orderservice.api.dto;

import com.paymentplatform.commonlib.enums.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Request body for {@code POST /api/orders}.
 *
 * <p>All fields are validated via Jakarta Bean Validation.
 * The nested {@link OrderItemRequest} items are validated transitively via {@code @Valid}.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateOrderRequest {

    /** Business customer identifier — must not be blank. */
    @NotBlank
    private String customerId;

    /** Customer's email for notifications — must be a valid email address. */
    @NotBlank
    @Email
    private String customerEmail;

    /** Payment method to use for this order. Currently only WALLET is active. */
    @NotNull
    private PaymentMethod paymentMethod;

    /** Delivery address — must not be blank. */
    @NotBlank
    private String shippingAddress;

    /** At least one item required. Each item is individually validated by {@code @Valid}. */
    @NotEmpty
    @Valid
    private List<OrderItemRequest> items;

    /**
     * A single line item within the order request.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OrderItemRequest {

        /** Product UUID — must match a product in inventory_db. */
        @NotBlank
        private String productId;

        /** Optional display name — stored on the order for historical reference. */
        private String productName;

        /** Optional SKU — stored on the order for historical reference. */
        private String sku;

        /** Units to order — must be at least 1. */
        @Min(1)
        private int quantity;

        /** Price per unit — must be positive. Used to compute subtotal. */
        @NotNull
        @DecimalMin("0.01")
        private BigDecimal unitPrice;

        /** ISO 4217 currency code — exactly 3 characters. */
        @NotNull
        @Size(min = 3, max = 3)
        private String currency;
    }
}
