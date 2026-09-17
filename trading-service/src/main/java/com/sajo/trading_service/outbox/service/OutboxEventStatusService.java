package com.sajo.trading_service.outbox.service;

import com.sajo.trading_service.outbox.domain.OutboxEvent;
import com.sajo.trading_service.outbox.domain.OutboxStatus;
import com.sajo.trading_service.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxEventStatusService {

    private static final int MAX_RETRY_COUNT = 3;
    private static final Duration PROCESSING_TIMEOUT = Duration.ofMinutes(1);

    private final OutboxEventRepository outboxEventRepository;

    @Transactional
    public boolean claimForPublish(UUID eventId){
        return outboxEventRepository.claimForPublish(
                eventId,
                OutboxStatus.PENDING,
                OutboxStatus.PROCESSING,
                Instant.now()
        ) == 1;
    }

    @Transactional
    public void markPublished(UUID eventId){
        OutboxEvent event = outboxEventRepository.findById(eventId).orElseThrow();
        event.markPublished();
    }

    @Transactional
    public OutboxEvent handlePublishFailure(UUID eventId){
        OutboxEvent event = outboxEventRepository.findById(eventId).orElseThrow();
        event.increaseRetryCount();

        if(event.getRetryCount() >= MAX_RETRY_COUNT){
            event.markFailed();
        } else {
            event.markPending();
        }

        return event;
    }

    @Transactional
    public int recoverStaleProcessingEvents() {
        Instant threshold = Instant.now().minus(PROCESSING_TIMEOUT);

        return outboxEventRepository.recoverStaleProcessingEvents(
                OutboxStatus.PROCESSING,
                OutboxStatus.PENDING,
                threshold
        );
    }
}
