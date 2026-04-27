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

/**
 * Spring Data JPA repository for {@link ReservationRecord} entities.
 *
 * <p>Each row represents one item-level stock reservation for an order. Reservations
 * transition through {@code ACTIVE → RELEASED} (on order completion or cancellation)
 * or {@code ACTIVE → EXPIRED} (when the TTL reaper fires). Multiple records may exist
 * per order — one per line item.</p>
 *
 * <p>Used by {@code InventoryService} for saga compensation and by
 * {@code ReservationExpiryProcessor} for TTL-based automatic release.</p>
 */
@Repository
public interface ReservationRecordRepository extends JpaRepository<ReservationRecord, UUID> {

    /**
     * Returns all reservation records for a given order in the specified status.
     * Used during cancellation or compensation to identify which items need stock released.
     *
     * @param orderId the order whose reservations are to be retrieved
     * @param status  the reservation status filter (typically {@link ReservationStatus#ACTIVE})
     * @return list of matching reservation records
     */
    List<ReservationRecord> findByOrderIdAndStatus(String orderId, ReservationStatus status);

    /**
     * Returns all {@code ACTIVE} reservations whose {@code reservedAt} timestamp is before
     * the given cutoff instant. Used by {@code ReservationExpiryProcessor} to find reservations
     * that have outlived their TTL and should be automatically released.
     *
     * @param status  the status to check (should be {@link ReservationStatus#ACTIVE})
     * @param cutoff  reservations older than this instant are considered expired
     * @return list of expired reservation records
     */
    @Query("SELECT r FROM ReservationRecord r WHERE r.status = :status AND r.reservedAt < :cutoff")
    List<ReservationRecord> findExpiredReservations(@Param("status") ReservationStatus status, @Param("cutoff") Instant cutoff);
}
