package com.paymentplatform.inventoryservice.application.mapper;

import com.paymentplatform.commonlib.dto.InventoryDto;
import com.paymentplatform.inventoryservice.domain.entity.Inventory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper that converts {@link Inventory} JPA entities to
 * {@link com.paymentplatform.commonlib.dto.InventoryDto} API objects.
 *
 * <p>The {@link Inventory} entity holds a LAZY {@code @ManyToOne} to {@link com.paymentplatform.inventoryservice.domain.entity.Product}.
 * Callers must ensure the product is loaded (use {@code findByProductIdWithProduct()} or
 * {@code findAllWithProduct()}) before calling {@link #toDto(Inventory)}, otherwise MapStruct
 * will trigger a lazy-load outside a transaction and throw a {@code LazyInitializationException}.</p>
 *
 * <p>Field mappings that differ from default by-name convention:</p>
 * <ul>
 *   <li>{@code product.id} → {@code productId}</li>
 *   <li>{@code product.name} → {@code productName}</li>
 *   <li>{@code product.sku} → {@code sku}</li>
 *   <li>{@code updatedAt} → {@code lastUpdatedAt}</li>
 * </ul>
 */
@Mapper(componentModel = "spring")
public interface InventoryMapper {

    /**
     * Maps an {@link Inventory} entity (with loaded product) to its DTO representation.
     *
     * @param inventory inventory entity; {@code product} association must be initialised
     * @return populated {@link com.paymentplatform.commonlib.dto.InventoryDto}
     */
    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "productName", source = "product.name")
    @Mapping(target = "sku", source = "product.sku")
    @Mapping(target = "lastUpdatedAt", source = "updatedAt")
    InventoryDto toDto(Inventory inventory);
}
