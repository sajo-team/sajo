package com.sajo.trading_service.outbox.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajo.trading_service.ai_risk.kafka.dto.AiRiskAnalysisRequestedEvent;
import com.sajo.trading_service.outbox.domain.OutboxEvent;
import com.sajo.trading_service.outbox.domain.OutboxMessage;
import com.sajo.trading_service.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OutboxEventService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public void save(OutboxMessage message){

        JsonNode eventBody = objectMapper.valueToTree(message.eventBody());

        OutboxEvent outboxEvent = OutboxEvent.create(
                message.eventId(),
                message.eventType(),
                message.eventVersion(),
                eventBody
        );

        outboxEventRepository.save(outboxEvent);
    }
}
