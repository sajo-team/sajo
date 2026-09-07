package com.sajo.trading_service.ai_risk.service.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.ai_risk.document.AiAnalysisHistory;
import com.sajo.trading_service.ai_risk.domain.AiAnalysisFailureType;
import com.sajo.trading_service.ai_risk.domain.AiAnalysisStatus;
import com.sajo.trading_service.ai_risk.exception.AiRiskErrorCode;
import com.sajo.trading_service.ai_risk.repository.query.AiAnalysisHistoryQueryRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("ai-risk")
@ExtendWith(MockitoExtension.class)
class AiAnalysisHistoryQueryServiceTest {

    @Mock
    private AiAnalysisHistoryQueryRepository aiAnalysisHistoryQueryRepository;

    @InjectMocks
    private AiAnalysisHistoryQueryService aiAnalysisHistoryQueryService;

    @Test
    void Audit_상세_조회에_성공한다() {

        UUID analysisId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID strategyId = UUID.randomUUID();
        UUID backtestId = UUID.randomUUID();

        AiAnalysisHistory history = AiAnalysisHistory.builder()
                .analysisId(analysisId)
                .userId(userId)
                .strategyId(strategyId)
                .backtestId(backtestId)
                .requestSnapshot(Map.of(
                        "strategy", Map.of("name", "test-strategy")
                ))
                .prompt(new AiAnalysisHistory.PromptSnapshot(
                        "v1",
                        "위험도를 분석해주세요."
                ))
                .response(new AiAnalysisHistory.ResponseSnapshot(
                        "{\"riskLevel\":\"LOW\"}"
                ))
                .validation(new AiAnalysisHistory.ValidationSnapshot(
                        true,
                        true,
                        List.of()
                ))
                .metadata(new AiAnalysisHistory.MetadataSnapshot(
                        "test-model",
                        1000L
                ))
                .result(new AiAnalysisHistory.ResultSnapshot(
                        AiAnalysisStatus.COMPLETED,
                        null
                ))
                .build();

        when(aiAnalysisHistoryQueryRepository.findByAnalysisId(analysisId))
                .thenReturn(Optional.of(history));

        var response =
                aiAnalysisHistoryQueryService.getAuditDetail(analysisId);

        assertThat(response.analysisId()).isEqualTo(analysisId);
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.strategyId()).isEqualTo(strategyId);
        assertThat(response.backtestId()).isEqualTo(backtestId);

        assertThat(response.prompt().version()).isEqualTo("v1");
        assertThat(response.prompt().content())
                .isEqualTo("위험도를 분석해주세요.");

        assertThat(response.response().rawResponse())
                .isEqualTo("{\"riskLevel\":\"LOW\"}");

        assertThat(response.validation().structureValid()).isTrue();
        assertThat(response.validation().contentValid()).isTrue();
        assertThat(response.validation().errors()).isEmpty();

        assertThat(response.metadata().model()).isEqualTo("test-model");
        assertThat(response.metadata().latencyMs()).isEqualTo(1000L);

        assertThat(response.result()).isNotNull();
        assertThat(response.result().status())
                .isEqualTo(AiAnalysisStatus.COMPLETED);
        assertThat(response.result().failureType()).isNull();

        verify(aiAnalysisHistoryQueryRepository)
                .findByAnalysisId(analysisId);
    }

    @Test
    void Audit_이력이_없으면_예외가_발생한다() {

        UUID analysisId = UUID.randomUUID();

        when(aiAnalysisHistoryQueryRepository.findByAnalysisId(analysisId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> aiAnalysisHistoryQueryService.getAuditDetail(analysisId)
        )
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(
                                        AiRiskErrorCode.AUDIT_HISTORY_NOT_FOUND
                                )
                );

        verify(aiAnalysisHistoryQueryRepository)
                .findByAnalysisId(analysisId);
    }

    @Test
    void 일부_Snapshot이_null인_실패_Audit도_조회할_수_있다() {

        UUID analysisId = UUID.randomUUID();

        AiAnalysisHistory history = AiAnalysisHistory.builder()
                .analysisId(analysisId)
                .userId(UUID.randomUUID())
                .strategyId(UUID.randomUUID())
                .backtestId(UUID.randomUUID())
                .requestSnapshot(Map.of())
                .validation(new AiAnalysisHistory.ValidationSnapshot(
                        false,
                        false,
                        List.of("프롬프트를 찾을 수 없습니다.")
                ))
                .result(new AiAnalysisHistory.ResultSnapshot(
                        AiAnalysisStatus.FAILED,
                        AiAnalysisFailureType.PROMPT_NOT_FOUND
                ))
                .build();

        when(aiAnalysisHistoryQueryRepository.findByAnalysisId(analysisId))
                .thenReturn(Optional.of(history));

        var response =
                aiAnalysisHistoryQueryService.getAuditDetail(analysisId);

        assertThat(response.prompt()).isNull();
        assertThat(response.response()).isNull();
        assertThat(response.metadata()).isNull();

        assertThat(response.validation()).isNotNull();
        assertThat(response.validation().structureValid()).isFalse();
        assertThat(response.validation().contentValid()).isFalse();

        assertThat(response.result()).isNotNull();
        assertThat(response.result().status())
                .isEqualTo(AiAnalysisStatus.FAILED);
        assertThat(response.result().failureType())
                .isEqualTo(AiAnalysisFailureType.PROMPT_NOT_FOUND);

        verify(aiAnalysisHistoryQueryRepository)
                .findByAnalysisId(analysisId);
    }
}