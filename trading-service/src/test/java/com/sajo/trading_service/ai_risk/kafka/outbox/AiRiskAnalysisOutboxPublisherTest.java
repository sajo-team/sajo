package com.sajo.trading_service.ai_risk.kafka.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajo.trading_service.ai_risk.kafka.dto.AiRiskAnalysisRequestedEvent;
import com.sajo.trading_service.ai_risk.kafka.producer.AiRiskAnalysisEventProducer;
import com.sajo.trading_service.outbox.domain.OutboxEvent;
import com.sajo.trading_service.outbox.domain.OutboxStatus;
import com.sajo.trading_service.outbox.repository.OutboxEventRepository;
import com.sajo.trading_service.outbox.service.OutboxEventStatusService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("ai-risk")
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class AiRiskAnalysisOutboxPublisherTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private AiRiskAnalysisEventProducer eventProducer;

    @Mock
    private OutboxEventStatusService outboxEventStatusService;

    @Mock
    private ObjectMapper objectMapper;

    private AiRiskAnalysisOutboxPublisher publisher;

    private UUID eventId;
    private OutboxEvent outboxEvent;
    private AiRiskAnalysisRequestedEvent event;

    @BeforeEach
    void setUp() {
        eventId = UUID.randomUUID();

        outboxEvent = OutboxEvent.create(
                eventId,
                AiRiskAnalysisRequestedEvent.EVENT_TYPE,
                AiRiskAnalysisRequestedEvent.EVENT_VERSION,
                null
        );

        event = mock(AiRiskAnalysisRequestedEvent.class);

        publisher = new AiRiskAnalysisOutboxPublisher(
                outboxEventRepository,
                eventProducer,
                objectMapper,
                outboxEventStatusService
        );
    }

    @Test
    void Outbox_선점_후_Kafka_발행에_성공하면_PUBLISHED로_변경한다() throws Exception {
        givenPendingEvent();

        when(outboxEventStatusService.claimForPublish(eventId))
                .thenReturn(true);

        when(objectMapper.treeToValue(
                outboxEvent.getEventBody(),
                AiRiskAnalysisRequestedEvent.class
        )).thenReturn(event);

        publisher.publishPendingEvents();

        verify(eventProducer).publish(event);
        verify(outboxEventStatusService).markPublished(eventId);
        verify(outboxEventStatusService, never())
                .handlePublishFailure(any());
    }

    @Test
    void Outbox_선점에_실패하면_Kafka에_발행하지_않는다() {
        givenPendingEvent();

        when(outboxEventStatusService.claimForPublish(eventId))
                .thenReturn(false);

        publisher.publishPendingEvents();

        verifyNoInteractions(eventProducer);
        verify(outboxEventStatusService, never())
                .markPublished(any());
    }

    @Test
    void Kafka_발행에_실패하면_Outbox_실패를_처리한다() throws Exception {
        givenPendingEvent();

        when(outboxEventStatusService.claimForPublish(eventId))
                .thenReturn(true);

        when(objectMapper.treeToValue(
                outboxEvent.getEventBody(),
                AiRiskAnalysisRequestedEvent.class
        )).thenReturn(event);

        doThrow(new IllegalStateException("Kafka publish failed"))
                .when(eventProducer)
                .publish(event);

        when(outboxEventStatusService.handlePublishFailure(eventId))
                .thenReturn(outboxEvent);

        publisher.publishPendingEvents();

        verify(outboxEventStatusService)
                .handlePublishFailure(eventId);

        verify(outboxEventStatusService, never())
                .markPublished(any());
    }

    @Test
    void Kafka_발행_성공_후_상태_갱신에_실패해도_발행_실패로_처리하지_않는다()
            throws Exception {

        givenPendingEvent();

        when(outboxEventStatusService.claimForPublish(eventId))
                .thenReturn(true);

        when(objectMapper.treeToValue(
                outboxEvent.getEventBody(),
                AiRiskAnalysisRequestedEvent.class
        )).thenReturn(event);

        doThrow(new IllegalStateException("DB update failed"))
                .when(outboxEventStatusService)
                .markPublished(eventId);

        publisher.publishPendingEvents();

        verify(eventProducer).publish(event);
        verify(outboxEventStatusService).markPublished(eventId);

        verify(outboxEventStatusService, never())
                .handlePublishFailure(any());
    }

    private void givenPendingEvent() {
        when(outboxEventRepository
                .findByStatusAndEventTypeOrderByCreatedAtAsc(
                        eq(OutboxStatus.PENDING),
                        eq(AiRiskAnalysisRequestedEvent.EVENT_TYPE),
                        any(PageRequest.class)
                ))
                .thenReturn(List.of(outboxEvent));
    }
}