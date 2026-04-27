package com.paymentplatform.inventoryservice.domain.repository;

import com.paymentplatform.inventoryservice.domain.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Product} entities.
 *
 * <p>Products are the catalogue records containing metadata (name, description, price, SKU).
 * Live stock counters are kept in the separate {@link Inventory} entity. Product rows are
 * created by Flyway seed migrations and are not mutated at runtime in normal operations.</p>
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, UUID> {

    /**
     * Looks up a product by its unique SKU identifier.
     * Used during reservation processing to resolve incoming SKU references from events.
     *
     * @param sku the stock-keeping unit identifier
     * @return the matching product, or empty if the SKU is unknown
     */
    Optional<Product> findBySku(String sku);

    /**
     * Returns all active (non-archived) products.
     * An {@code active=false} product is excluded from listings and cannot be reserved.
     *
     * @return list of active products
     */
    List<Product> findByActiveTrue();

    /**
     * Checks whether a product with the given SKU already exists.
     * Used during product creation to enforce uniqueness before attempting an insert.
     *
     * @param sku the SKU to check
     * @return {@code true} if the SKU is already registered
     */
    boolean existsBySku(String sku);
}
