package com.paymentplatform.inventoryservice.application.mapper;

import com.paymentplatform.commonlib.dto.InventoryDto;
import com.paymentplatform.inventoryservice.domain.entity.Inventory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface InventoryMapper {

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "productName", source = "product.name")
    @Mapping(target = "sku", source = "product.sku")
    @Mapping(target = "lastUpdatedAt", source = "updatedAt")
    InventoryDto toDto(Inventory inventory);
}
