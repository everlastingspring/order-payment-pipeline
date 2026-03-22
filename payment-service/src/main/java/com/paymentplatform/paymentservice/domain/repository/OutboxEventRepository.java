package com.paymentplatform.paymentservice.domain.repository;

import com.paymentplatform.paymentservice.domain.entity.OutboxEvent;
import com.paymentplatform.paymentservice.domain.entity.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus status);
}
