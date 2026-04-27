package com.paymentplatform.orderservice.domain.repository;

import com.paymentplatform.commonlib.enums.OrderStatus;
import com.paymentplatform.orderservice.domain.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Order} entities.
 *
 * <p>Provides standard CRUD operations via {@link JpaRepository} plus domain-specific
 * query methods used by {@code OrderService} and {@code OrderSagaManager}.</p>
 *
 * <p>All write operations that mutate order status should be executed inside a
 * {@code @Transactional} service method. This repository does not enforce
 * business-rule constraints — those live in the service layer.</p>
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    /**
     * Returns all orders for a given customer, newest first.
     * Used by the customer-orders list endpoint to support paginated history.
     *
     * @param customerId external customer identifier (e.g. {@code "customer-001"})
     * @param pageable   pagination and sorting parameters
     * @return page of matching orders ordered by {@code createdAt DESC}
     */
    Page<Order> findByCustomerIdOrderByCreatedAtDesc(String customerId, Pageable pageable);

    /**
     * Returns all orders in a given status, newest first.
     * Intended for operational/admin views (e.g. listing all {@code PENDING} orders).
     *
     * @param status   the order status filter
     * @param pageable pagination and sorting parameters
     * @return page of matching orders ordered by {@code createdAt DESC}
     */
    Page<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status, Pageable pageable);
}
