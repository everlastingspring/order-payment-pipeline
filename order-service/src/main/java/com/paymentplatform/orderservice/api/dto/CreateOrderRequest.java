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

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateOrderRequest {

    @NotBlank
    private String customerId;

    @NotBlank
    @Email
    private String customerEmail;

    @NotNull
    private PaymentMethod paymentMethod;

    @NotBlank
    private String shippingAddress;

    @NotEmpty
    @Valid
    private List<OrderItemRequest> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OrderItemRequest {

        @NotBlank
        private String productId;

        private String productName;

        private String sku;

        @Min(1)
        private int quantity;

        @NotNull
        @DecimalMin("0.01")
        private BigDecimal unitPrice;

        @NotNull
        @Size(min = 3, max = 3)
        private String currency;
    }
}
