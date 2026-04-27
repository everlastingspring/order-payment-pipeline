package com.paymentplatform.inventoryservice.domain.repository;

import com.paymentplatform.commonlib.enums.InventoryStatus;
import com.paymentplatform.inventoryservice.domain.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Inventory} entities.
 *
 * <p>All queries use {@code JOIN FETCH i.product} to avoid N+1 lazy-loading issues
 * when the {@link com.paymentplatform.inventoryservice.application.mapper.InventoryMapper}
 * accesses product fields during DTO conversion.</p>
 *
 * <p>The {@code Inventory} entity holds the live stock counters
 * ({@code availableQuantity}, {@code reservedQuantity}) for a product at a warehouse.
 * All mutations are performed inside {@code @Transactional} service methods.</p>
 */
@Repository
public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    /**
     * Finds the inventory record for a product across the default warehouse, eagerly
     * fetching the associated {@link com.paymentplatform.inventoryservice.domain.entity.Product}.
     * Used during the two-pass reservation and for the single-product query endpoint.
     *
     * @param productId product identifier
     * @return inventory record with product loaded, or empty if not found
     */
    @Query("SELECT i FROM Inventory i JOIN FETCH i.product WHERE i.product.id = :productId")
    Optional<Inventory> findByProductIdWithProduct(@Param("productId") UUID productId);

    /**
     * Finds the inventory record for a specific product + warehouse combination,
     * eagerly fetching the associated product. Used when multi-warehouse support
     * requires warehouse-scoped reservation.
     *
     * @param productId   product identifier
     * @param warehouseId warehouse identifier
     * @return inventory record with product loaded, or empty if not found
     */
    @Query("SELECT i FROM Inventory i JOIN FETCH i.product WHERE i.product.id = :productId AND i.warehouseId = :warehouseId")
    Optional<Inventory> findByProductIdAndWarehouseId(@Param("productId") UUID productId, @Param("warehouseId") String warehouseId);

    /**
     * Returns all inventory records in a given status (e.g. {@code IN_STOCK}, {@code OUT_OF_STOCK}),
     * with product data eagerly loaded.
     *
     * @param status the inventory status filter
     * @return list of matching inventory records with products loaded
     */
    @Query("SELECT i FROM Inventory i JOIN FETCH i.product WHERE i.status = :status")
    List<Inventory> findByStatus(@Param("status") InventoryStatus status);

    /**
     * Returns all inventory records with product data eagerly loaded.
     * Used by the list-all endpoint — avoids N+1 that would occur with plain {@code findAll()}.
     *
     * @return all inventory records with products loaded
     */
    @Query("SELECT i FROM Inventory i JOIN FETCH i.product")
    List<Inventory> findAllWithProduct();

    /**
     * Returns inventory records whose {@code availableQuantity} is at or below the given
     * threshold, ordered by quantity ascending (lowest stock first). Intended for
     * low-stock alerting or restocking workflows.
     *
     * @param threshold maximum available quantity to include in the results
     * @return inventory records with low stock, ascending by available quantity
     */
    @Query("SELECT i FROM Inventory i JOIN FETCH i.product WHERE i.availableQuantity <= :threshold ORDER BY i.availableQuantity ASC")
    List<Inventory> findLowStock(@Param("threshold") int threshold);
}
