package com.sajo.trading_service.ai_risk.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.ai_risk.domain.*;
import com.sajo.trading_service.ai_risk.repository.command.AiRiskAnalysisCommandRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("ai-risk")
@ExtendWith(MockitoExtension.class)
class AiRiskAnalysisResultServiceTest {

    @Mock
    private AiRiskAnalysisCommandRepository repository;

    @InjectMocks
    private AiRiskAnalysisResultService resultService;

    private UUID analysisId;
    private UUID userId;
    private UUID strategyId;
    private UUID backtestId;

    private AiRiskAnalysis analysis;

    @BeforeEach
    void setUp() {
        analysisId = UUID.randomUUID();
        userId = UUID.randomUUID();
        strategyId = UUID.randomUUID();
        backtestId = UUID.randomUUID();

        analysis = AiRiskAnalysis.create(
                userId,
                strategyId,
                backtestId
        );
    }

    @Test
    @DisplayName("AI 분석을 완료하면 분석 결과와 COMPLETED 상태가 저장된다")
    void complete_success() {
        List<RiskFactor> riskFactors = List.of(
                new RiskFactor(
                        RiskFactorType.MAX_DRAWDOWN,
                        "최대 낙폭이 높습니다."
                )
        );

        List<String> recommendations = List.of(
                "손절 기준을 검토하세요."
        );

        when(repository.findById(analysisId))
                .thenReturn(Optional.of(analysis));

        resultService.complete(
                analysisId,
                RiskLevel.HIGH,
                "위험도가 높은 전략입니다.",
                riskFactors,
                "최대 낙폭을 기준으로 판단했습니다.",
                recommendations
        );

        assertThat(analysis.getStatus())
                .isEqualTo(AiAnalysisStatus.COMPLETED);

        assertThat(analysis.getRiskLevel())
                .isEqualTo(RiskLevel.HIGH);

        assertThat(analysis.getSummary())
                .isEqualTo("위험도가 높은 전략입니다.");

        assertThat(analysis.getRiskFactors())
                .isEqualTo(riskFactors);

        assertThat(analysis.getReasoning())
                .isEqualTo("최대 낙폭을 기준으로 판단했습니다.");

        assertThat(analysis.getRecommendations())
                .isEqualTo(recommendations);

        assertThat(analysis.getFailureType()).isNull();
        assertThat(analysis.getFailureMessage()).isNull();

        verify(repository).findById(analysisId);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("AI 분석에 실패하면 FAILED 상태와 실패 정보가 저장된다")
    void fail_success() {
        when(repository.findById(analysisId))
                .thenReturn(Optional.of(analysis));

        resultService.fail(
                analysisId,
                AiAnalysisFailureType.LLM_API_ERROR,
                "LLM API 호출에 실패했습니다."
        );

        assertThat(analysis.getStatus())
                .isEqualTo(AiAnalysisStatus.FAILED);

        assertThat(analysis.getFailureType())
                .isEqualTo(AiAnalysisFailureType.LLM_API_ERROR);

        assertThat(analysis.getFailureMessage())
                .isEqualTo("LLM API 호출에 실패했습니다.");

        verify(repository).findById(analysisId);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("존재하지 않는 분석의 결과를 변경하려 하면 예외가 발생한다")
    void complete_analysisNotFound_throwsException() {
        when(repository.findById(analysisId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                resultService.complete(
                        analysisId,
                        RiskLevel.LOW,
                        "요약",
                        List.of(),
                        "분석 근거",
                        List.of()
                )
        ).isInstanceOf(BusinessException.class);

        verify(repository).findById(analysisId);
    }
}