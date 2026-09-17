package com.sajo.trading_service.outbox.service;

import com.sajo.trading_service.outbox.domain.OutboxEvent;
import com.sajo.trading_service.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxEventStatusService {

    private static final int MAX_RETRY_COUNT = 3;

    private final OutboxEventRepository outboxEventRepository;

    @Transactional
    public void markPublished(UUID eventId){
        OutboxEvent event = outboxEventRepository.findById(eventId).orElseThrow();
        event.markPublished();
    }

    @Transactional
    public void handlePublishFailure(UUID eventId){
        OutboxEvent event = outboxEventRepository.findById(eventId).orElseThrow();
        event.increaseRetryCount();

        if(event.getRetryCount() >= MAX_RETRY_COUNT){
            event.markFailed();
        }
    }
}
