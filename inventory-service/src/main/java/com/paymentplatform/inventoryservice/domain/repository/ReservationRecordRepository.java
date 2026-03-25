package com.paymentplatform.inventoryservice.domain.repository;

import com.paymentplatform.inventoryservice.domain.entity.ReservationRecord;
import com.paymentplatform.inventoryservice.domain.entity.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface ReservationRecordRepository extends JpaRepository<ReservationRecord, UUID> {

    List<ReservationRecord> findByOrderIdAndStatus(String orderId, ReservationStatus status);

    @Query("SELECT r FROM ReservationRecord r WHERE r.status = :status AND r.reservedAt < :cutoff")
    List<ReservationRecord> findExpiredReservations(@Param("status") ReservationStatus status, @Param("cutoff") Instant cutoff);
}
