package com.sajo.trading_service.outbox.service;

import com.sajo.trading_service.outbox.domain.OutboxEvent;
import com.sajo.trading_service.outbox.domain.OutboxStatus;
import com.sajo.trading_service.outbox.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@Tag("ai-risk")
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class OutboxEventStatusServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private OutboxEventStatusService outboxEventStatusService;

    private UUID eventId;
    private OutboxEvent outboxEvent;

    @BeforeEach
    void setUp() {
        eventId = UUID.randomUUID();

        outboxEvent = OutboxEvent.create(
                eventId,
                "AI_RISK_ANALYSIS_REQUESTED",
                1,
                null
        );

        outboxEventStatusService =
                new OutboxEventStatusService(outboxEventRepository);
    }

    @Test
    void Outbox_이벤트_선점에_성공하면_true를_반환한다() {
        when(outboxEventRepository.claimForPublish(
                eventId,
                OutboxStatus.PENDING,
                OutboxStatus.PROCESSING
        )).thenReturn(1);

        boolean claimed = outboxEventStatusService.claimForPublish(eventId);

        assertThat(claimed).isTrue();
    }

    @Test
    void 이미_선점된_Outbox_이벤트이면_false를_반환한다() {
        when(outboxEventRepository.claimForPublish(
                eventId,
                OutboxStatus.PENDING,
                OutboxStatus.PROCESSING
        )).thenReturn(0);

        boolean claimed = outboxEventStatusService.claimForPublish(eventId);

        assertThat(claimed).isFalse();
    }

    @Test
    void 발행_성공_시_PUBLISHED_상태로_변경된다() {
        when(outboxEventRepository.findById(eventId))
                .thenReturn(Optional.of(outboxEvent));

        outboxEventStatusService.markPublished(eventId);

        assertThat(outboxEvent.getStatus())
                .isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(outboxEvent.getPublishedAt()).isNotNull();
    }

    @Test
    void 발행_실패가_최대_재시도_미만이면_PENDING을_유지한다() {
        when(outboxEventRepository.findById(eventId))
                .thenReturn(Optional.of(outboxEvent));

        outboxEventStatusService.handlePublishFailure(eventId);
        outboxEventStatusService.handlePublishFailure(eventId);

        assertThat(outboxEvent.getRetryCount()).isEqualTo(2);
        assertThat(outboxEvent.getStatus())
                .isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    void 발행_실패가_최대_재시도에_도달하면_FAILED로_변경된다() {
        when(outboxEventRepository.findById(eventId))
                .thenReturn(Optional.of(outboxEvent));

        outboxEventStatusService.handlePublishFailure(eventId);
        outboxEventStatusService.handlePublishFailure(eventId);
        outboxEventStatusService.handlePublishFailure(eventId);

        assertThat(outboxEvent.getRetryCount()).isEqualTo(3);
        assertThat(outboxEvent.getStatus())
                .isEqualTo(OutboxStatus.FAILED);
    }
}