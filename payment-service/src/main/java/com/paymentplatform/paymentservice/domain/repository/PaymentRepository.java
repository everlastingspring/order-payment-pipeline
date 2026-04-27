package com.paymentplatform.paymentservice.domain.repository;

import com.paymentplatform.commonlib.enums.PaymentStatus;
import com.paymentplatform.paymentservice.domain.entity.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Payment} entities.
 *
 * <p>Provides standard CRUD operations plus domain-specific query methods for
 * lookup by order, idempotency key, customer, and status. Writes are always
 * performed inside {@code @Transactional} service methods.</p>
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /**
     * Looks up the payment record associated with a given order.
     * Used by the saga to detect existing payments and by the query controller.
     *
     * @param orderId the order identifier (UUID string from common-lib events)
     * @return the payment for this order, or empty if not yet created
     */
    Optional<Payment> findByOrderId(String orderId);

    /**
     * Looks up a payment by its idempotency key.
     * Used for double-charge prevention — if a key already exists the original
     * payment result is returned rather than processing a new charge.
     *
     * @param idempotencyKey the client-supplied idempotency key
     * @return the payment that consumed this key, or empty if not found
     */
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    /**
     * Returns all payments for a given customer, newest first.
     * Used by the customer payment-history endpoint.
     *
     * @param customerId external customer identifier
     * @param pageable   pagination and sorting parameters
     * @return page of matching payments ordered by {@code createdAt DESC}
     */
    Page<Payment> findByCustomerIdOrderByCreatedAtDesc(String customerId, Pageable pageable);

    /**
     * Returns all payments in a given status, newest first.
     * Intended for operational views (e.g. listing all {@code FAILED} payments).
     *
     * @param status   the payment status filter
     * @param pageable pagination and sorting parameters
     * @return page of matching payments ordered by {@code createdAt DESC}
     */
    Page<Payment> findByStatusOrderByCreatedAtDesc(PaymentStatus status, Pageable pageable);

    /**
     * Checks whether a payment in one of the given terminal statuses already exists for
     * the specified order. Used by idempotency guards to prevent re-processing.
     *
     * @param orderId  the order to check
     * @param statuses list of statuses to test membership against (e.g. COMPLETED, FAILED)
     * @return {@code true} if a matching payment exists
     */
    boolean existsByOrderIdAndStatusIn(String orderId, java.util.List<PaymentStatus> statuses);
}
