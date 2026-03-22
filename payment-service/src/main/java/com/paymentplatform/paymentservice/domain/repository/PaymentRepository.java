package com.paymentplatform.paymentservice.domain.repository;

import com.paymentplatform.commonlib.enums.PaymentStatus;
import com.paymentplatform.paymentservice.domain.entity.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByOrderId(String orderId);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    Page<Payment> findByCustomerIdOrderByCreatedAtDesc(String customerId, Pageable pageable);

    Page<Payment> findByStatusOrderByCreatedAtDesc(PaymentStatus status, Pageable pageable);

    boolean existsByOrderIdAndStatusIn(String orderId, java.util.List<PaymentStatus> statuses);
}
