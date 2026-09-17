package com.sajo.trading_service.outbox.repository;

import com.sajo.trading_service.outbox.domain.OutboxEvent;
import com.sajo.trading_service.outbox.domain.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findByStatusAndEventTypeOrderByCreatedAtAsc(
            OutboxStatus status,
            String eventType,
            Pageable pageable
    );

    @Modifying
    @Query("""
    UPDATE OutboxEvent e
       SET e.status = :processingStatus
     WHERE e.id = :eventId
       AND e.status = :pendingStatus
    """)
    int claimForPublish(
            @Param("eventId") UUID eventId,
            @Param("pendingStatus") OutboxStatus pendingStatus,
            @Param("processingStatus") OutboxStatus processingStatus
    );
}
