package com.paymentplatform.commonlib.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Value object representing a monetary amount with its currency.
 *
 * <p>Used in events ({@code totalAmount}, {@code refundAmount}) and API responses
 * to keep amount and currency co-located and avoid unit confusion.</p>
 *
 * <p>Decision: using {@code BigDecimal} (not {@code double}) to prevent
 * floating-point rounding errors on financial arithmetic.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MoneyDto {

    /** Monetary amount — must be non-negative. */
    @NotNull
    @DecimalMin("0.00")
    private BigDecimal amount;

    /** ISO 4217 currency code — exactly 3 characters (e.g. "INR", "USD"). */
    @NotNull
    @Size(min = 3, max = 3)
    private String currency;
}
