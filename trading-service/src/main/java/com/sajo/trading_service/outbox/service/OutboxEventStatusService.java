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

    private final OutboxEventRepository outboxEventRepository;

    @Transactional
    public void markPublished(UUID eventId){
        OutboxEvent event = outboxEventRepository.findById(eventId).orElseThrow();
        event.markPublished();
    }

    @Transactional
    public void increaseRetryCount(UUID eventId){
        OutboxEvent event = outboxEventRepository.findById(eventId).orElseThrow();
        event.increaseRetryCount();
    }
}
