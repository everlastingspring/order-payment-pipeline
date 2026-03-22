package com.paymentplatform.commonlib.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.paymentplatform.commonlib.enums.InventoryStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InventoryDto {

    private String productId;
    private String productName;
    private String sku;
    private int availableQuantity;
    private int reservedQuantity;
    private InventoryStatus status;
    private String warehouseId;
    private Instant lastUpdatedAt;
}
