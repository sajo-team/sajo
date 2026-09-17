package com.sajo.trading_service.outbox.repository;

import com.sajo.trading_service.outbox.domain.OutboxEvent;
import com.sajo.trading_service.outbox.domain.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findByStatusAndEventTypeOrderByCreatedAtAsc(
            OutboxStatus status,
            String eventType,
            Pageable pageable
    );
}
