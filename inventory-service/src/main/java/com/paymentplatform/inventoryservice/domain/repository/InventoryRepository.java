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

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    @Query("SELECT i FROM Inventory i JOIN FETCH i.product WHERE i.product.id = :productId")
    Optional<Inventory> findByProductIdWithProduct(@Param("productId") UUID productId);

    @Query("SELECT i FROM Inventory i JOIN FETCH i.product WHERE i.product.id = :productId AND i.warehouseId = :warehouseId")
    Optional<Inventory> findByProductIdAndWarehouseId(@Param("productId") UUID productId, @Param("warehouseId") String warehouseId);

    @Query("SELECT i FROM Inventory i JOIN FETCH i.product WHERE i.status = :status")
    List<Inventory> findByStatus(@Param("status") InventoryStatus status);

    @Query("SELECT i FROM Inventory i JOIN FETCH i.product")
    List<Inventory> findAllWithProduct();
}
