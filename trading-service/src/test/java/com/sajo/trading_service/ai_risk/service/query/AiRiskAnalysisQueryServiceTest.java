package com.sajo.trading_service.ai_risk.service.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.ai_risk.controller.dto.response.AiRiskAnalysisDetailResponse;
import com.sajo.trading_service.ai_risk.domain.AiRiskAnalysis;
import com.sajo.trading_service.ai_risk.repository.query.AiRiskAnalysisQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("ai-risk")
@ExtendWith(MockitoExtension.class)
class AiRiskAnalysisQueryServiceTest {

    @Mock
    private AiRiskAnalysisQueryRepository queryRepository;

    @InjectMocks
    private AiRiskAnalysisQueryService queryService;

    private UUID userId;
    private UUID strategyId;
    private UUID backtestId;
    private UUID analysisId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        strategyId = UUID.randomUUID();
        backtestId = UUID.randomUUID();
        analysisId = UUID.randomUUID();
    }

    @Test
    @DisplayName("본인의 AI 위험 분석 결과를 상세 조회한다")
    void getAnalysis_success() {
        AiRiskAnalysis analysis =
                AiRiskAnalysis.create(userId, strategyId, backtestId);

        when(queryRepository.findByIdAndUserId(analysisId, userId))
                .thenReturn(Optional.of(analysis));

        AiRiskAnalysisDetailResponse response =
                queryService.getAnalysis(analysisId, userId);

        assertThat(response.strategyId()).isEqualTo(strategyId);
        assertThat(response.backtestId()).isEqualTo(backtestId);
        assertThat(response.status()).isEqualTo(analysis.getStatus());

        verify(queryRepository).findByIdAndUserId(analysisId, userId);
    }

    @Test
    @DisplayName("조회할 수 없는 AI 위험 분석이면 예외가 발생한다")
    void getAnalysis_notFound() {
        when(queryRepository.findByIdAndUserId(analysisId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                queryService.getAnalysis(analysisId, userId)
        ).isInstanceOf(BusinessException.class);

        verify(queryRepository).findByIdAndUserId(analysisId, userId);
    }
}