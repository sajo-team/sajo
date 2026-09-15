package com.sajo.user_service.account.service.query;

import com.sajo.user_service.account.controller.dto.response.TokenEventResponse;
import com.sajo.user_service.account.controller.dto.response.TokenStatusResponse;
import com.sajo.user_service.account.domain.EventType;
import com.sajo.user_service.account.domain.KisTokenLog;
import com.sajo.user_service.account.domain.KisTokenStatus;
import com.sajo.user_service.account.domain.KisTokenType;
import com.sajo.user_service.account.repository.query.KisTokenLogQueryRepository;
import com.sajo.user_service.account.repository.query.KisTokenStatusQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class KisTokenLogQueryServiceTest {

    @Mock
    private KisTokenLogQueryRepository kisTokenLogQueryRepository;

    @Mock
    private KisTokenStatusQueryRepository kisTokenStatusQueryRepository;

    private KisTokenLogQueryService kisTokenLogQueryService;

    @BeforeEach
    void setUp() {
        kisTokenLogQueryService =
                new KisTokenLogQueryService(kisTokenLogQueryRepository, kisTokenStatusQueryRepository);
    }

    @Test
    @DisplayName("getTokenStatuses는 KisTokenStatusQueryRepository(현재 상태 스냅샷)를 조회해 TokenStatusResponse로 매핑한다")
    void getTokenStatuses_delegatesToStatusRepositoryAndMaps() {
        // given
        UUID userId = UUID.randomUUID();
        Instant lastEventAt = Instant.now();
        KisTokenStatus status = mock(KisTokenStatus.class);
        given(status.getUserId()).willReturn(userId);
        given(status.getTokenType()).willReturn(KisTokenType.ACCESS_TOKEN);
        given(status.getEventType()).willReturn(EventType.TOKEN_ISSUE_FAILED);
        given(status.getErrorCode()).willReturn("EGW00133");
        given(status.getErrorMessage()).willReturn("1분당 1회 제한 초과");
        given(status.getLastEventAt()).willReturn(lastEventAt);

        Pageable pageable = PageRequest.of(0, 10);
        given(kisTokenStatusQueryRepository.findAll(pageable)).willReturn(new PageImpl<>(List.of(status)));

        // when
        Page<TokenStatusResponse> result = kisTokenLogQueryService.getTokenStatuses(pageable);

        // then - 자기조인 대신 스냅샷 테이블로 위임되고, 필드가 그대로 매핑돼야 한다
        assertThat(result.getContent()).hasSize(1);
        TokenStatusResponse response = result.getContent().get(0);
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.tokenType()).isEqualTo(KisTokenType.ACCESS_TOKEN);
        assertThat(response.eventType()).isEqualTo(EventType.TOKEN_ISSUE_FAILED);
        assertThat(response.errorCode()).isEqualTo("EGW00133");
        assertThat(response.errorMessage()).isEqualTo("1분당 1회 제한 초과");
        assertThat(response.createdAt()).isEqualTo(lastEventAt);
        verifyNoInteractions(kisTokenLogQueryRepository);
    }

    @Test
    @DisplayName("getTokenEventHistory는 특정 사용자의 이력을 KisTokenLogQueryRepository에서 조회해 TokenEventResponse로 매핑한다")
    void getTokenEventHistory_delegatesToLogRepositoryAndMaps() {
        // given
        UUID userId = UUID.randomUUID();
        Instant createdAt = Instant.now();
        KisTokenLog log = mock(KisTokenLog.class);
        given(log.getTokenType()).willReturn(KisTokenType.APPROVAL_KEY);
        given(log.getEventType()).willReturn(EventType.TOKEN_ISSUE_SUCCESS);
        given(log.getCreatedAt()).willReturn(createdAt);

        Pageable pageable = PageRequest.of(0, 10);
        given(kisTokenLogQueryRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable))
                .willReturn(new PageImpl<>(List.of(log)));

        // when
        Page<TokenEventResponse> result = kisTokenLogQueryService.getTokenEventHistory(userId, pageable);

        // then
        assertThat(result.getContent()).hasSize(1);
        TokenEventResponse response = result.getContent().get(0);
        assertThat(response.tokenType()).isEqualTo(KisTokenType.APPROVAL_KEY);
        assertThat(response.eventType()).isEqualTo(EventType.TOKEN_ISSUE_SUCCESS);
        assertThat(response.createdAt()).isEqualTo(createdAt);
        verifyNoInteractions(kisTokenStatusQueryRepository);
    }
}
