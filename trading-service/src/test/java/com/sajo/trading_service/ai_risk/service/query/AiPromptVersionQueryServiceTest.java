package com.sajo.trading_service.ai_risk.service.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.ai_risk.controller.dto.response.AiPromptVersionHistoryResponse;
import com.sajo.trading_service.ai_risk.document.AiAnalysisHistory;
import com.sajo.trading_service.ai_risk.domain.AiAnalysisFailureType;
import com.sajo.trading_service.ai_risk.domain.AiAnalysisStatus;
import com.sajo.trading_service.ai_risk.domain.AiPromptKey;
import com.sajo.trading_service.ai_risk.domain.AiPromptStatus;
import com.sajo.trading_service.ai_risk.domain.AiPromptVersion;
import com.sajo.trading_service.ai_risk.exception.AiRiskErrorCode;
import com.sajo.trading_service.ai_risk.repository.query.AiAnalysisHistoryQueryRepository;
import com.sajo.trading_service.ai_risk.repository.query.AiPromptVersionQueryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

@Tag("unit")
@Tag("ai-risk")
@ExtendWith(MockitoExtension.class)
class AiPromptVersionQueryServiceTest {

    @Mock
    private AiPromptVersionQueryRepository promptVersionQueryRepository;

    @Mock
    private AiAnalysisHistoryQueryRepository aiAnalysisHistoryQueryRepository;

    @InjectMocks
    private AiPromptVersionQueryService promptVersionQueryService;

    private AiAnalysisHistory createHistory(
            UUID analysisId,
            String version,
            AiAnalysisStatus status,
            AiAnalysisFailureType failureType
    ) {
        return AiAnalysisHistory.builder()
                .analysisId(analysisId)
                .userId(UUID.randomUUID())
                .strategyId(UUID.randomUUID())
                .backtestId(UUID.randomUUID())
                .prompt(new AiAnalysisHistory.PromptSnapshot(
                        version,
                        "테스트 프롬프트"
                ))
                .result(new AiAnalysisHistory.ResultSnapshot(
                        status,
                        failureType
                ))
                .build();
    }

    @Test
    @DisplayName("ACTIVE 프롬프트를 조회한다")
    void getActivePrompt() {
        // given
        AiPromptVersion promptVersion = AiPromptVersion.create(
                AiPromptKey.RISK_ANALYSIS,
                "v1",
                "위험 분석 프롬프트",
                "최초 등록"
        );

        given(promptVersionQueryRepository.findByPromptKeyAndStatus(
                AiPromptKey.RISK_ANALYSIS,
                AiPromptStatus.ACTIVE
        )).willReturn(Optional.of(promptVersion));

        // when
        AiPromptVersion result =
                promptVersionQueryService.getActivePrompt(
                        AiPromptKey.RISK_ANALYSIS
                );

        // then
        assertThat(result).isSameAs(promptVersion);
    }

    @Test
    @DisplayName("ACTIVE 프롬프트가 없으면 비즈니스 예외가 발생한다")
    void getActivePromptNotFound() {
        // given
        given(promptVersionQueryRepository.findByPromptKeyAndStatus(
                AiPromptKey.RISK_ANALYSIS,
                AiPromptStatus.ACTIVE
        )).willReturn(Optional.empty());

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> promptVersionQueryService.getActivePrompt(
                        AiPromptKey.RISK_ANALYSIS
                )
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(AiRiskErrorCode.AI_ACTIVE_PROMPT_NOT_FOUND);
    }

    @Test
    @DisplayName("프롬프트 버전별 분석 건수와 실패율 및 실패 유형을 집계한다")
    void getPromptVersionHistories() {
        // given
        AiPromptVersion promptVersion = AiPromptVersion.create(
                AiPromptKey.RISK_ANALYSIS,
                "v1",
                "위험 분석 프롬프트",
                "최초 등록"
        );

        PageRequest pageable = PageRequest.of(0, 10);

        given(promptVersionQueryRepository.findAll(pageable))
                .willReturn(new PageImpl<>(
                        List.of(promptVersion),
                        pageable,
                        1
                ));

        AiAnalysisHistory completedHistory1 = createHistory(
                UUID.randomUUID(),
                "v1",
                AiAnalysisStatus.COMPLETED,
                null
        );

        AiAnalysisHistory completedHistory2 = createHistory(
                UUID.randomUUID(),
                "v1",
                AiAnalysisStatus.COMPLETED,
                null
        );

        AiAnalysisHistory validationFailureHistory = createHistory(
                UUID.randomUUID(),
                "v1",
                AiAnalysisStatus.FAILED,
                AiAnalysisFailureType.VALIDATION_ERROR
        );

        AiAnalysisHistory parseFailureHistory = createHistory(
                UUID.randomUUID(),
                "v1",
                AiAnalysisStatus.FAILED,
                AiAnalysisFailureType.RESPONSE_PARSE_ERROR
        );

        given(aiAnalysisHistoryQueryRepository
                .findAllByPrompt_VersionIn(List.of("v1")))
                .willReturn(List.of(
                        completedHistory1,
                        completedHistory2,
                        validationFailureHistory,
                        parseFailureHistory
                ));

        // when
        Page<AiPromptVersionHistoryResponse> result =
                promptVersionQueryService.getPromptVersionHistories(pageable);

        // then
        assertThat(result.getTotalElements()).isEqualTo(1);

        AiPromptVersionHistoryResponse response =
                result.getContent().getFirst();

        assertThat(response.version()).isEqualTo("v1");
        assertThat(response.totalCount()).isEqualTo(4);
        assertThat(response.failedCount()).isEqualTo(2);
        assertThat(response.failureRate()).isEqualTo(50.0);

        assertThat(response.failureTypeCounts())
                .containsEntry(
                        AiAnalysisFailureType.VALIDATION_ERROR,
                        1L
                )
                .containsEntry(
                        AiAnalysisFailureType.RESPONSE_PARSE_ERROR,
                        1L
                );
    }
}