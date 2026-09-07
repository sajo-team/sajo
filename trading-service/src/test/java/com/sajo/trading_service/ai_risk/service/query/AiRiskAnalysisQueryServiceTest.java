package com.sajo.trading_service.ai_risk.service.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.ai_risk.controller.dto.response.AiRiskAnalysisDetailResponse;
import com.sajo.trading_service.ai_risk.controller.dto.response.AiRiskAnalysisFailureHistoryItemResponse;
import com.sajo.trading_service.ai_risk.controller.dto.response.AiRiskAnalysisHistoryItemResponse;
import com.sajo.trading_service.ai_risk.domain.*;
import com.sajo.trading_service.ai_risk.repository.query.AiRiskAnalysisQueryRepository;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.data.domain.Pageable;

import java.util.List;
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
        assertThat(response.status()).isEqualTo(AiAnalysisStatus.PENDING);
        assertThat(response.failureType()).isNull();
        assertThat(response.message()).isEqualTo("AI 분석이 진행 중입니다.");

        verify(queryRepository).findByIdAndUserId(analysisId, userId);
    }

    @Test
    @DisplayName("완료된 AI 위험 분석 조회 시 분석 결과와 완료 메시지를 반환한다")
    void getAnalysis_completed() {
        RiskFactor riskFactor = new RiskFactor(
                RiskFactorType.MAX_DRAWDOWN,
                "백테스트 결과 변동성이 높습니다."
        );

        AiRiskAnalysis analysis =
                AiRiskAnalysis.create(userId, strategyId, backtestId);

        analysis.complete(
                RiskLevel.MEDIUM,
                "중간 수준의 투자 위험이 있습니다.",
                List.of(riskFactor),
                "백테스트 결과 변동성이 확인되었습니다.",
                List.of("손실 한도를 낮추는 것을 고려하세요.")
        );

        when(queryRepository.findByIdAndUserId(analysisId, userId))
                .thenReturn(Optional.of(analysis));

        AiRiskAnalysisDetailResponse response =
                queryService.getAnalysis(analysisId, userId);

        assertThat(response.status())
                .isEqualTo(AiAnalysisStatus.COMPLETED);

        assertThat(response.riskLevel())
                .isEqualTo(RiskLevel.MEDIUM);

        assertThat(response.summary())
                .isEqualTo("중간 수준의 투자 위험이 있습니다.");

        assertThat(response.riskFactors())
                .containsExactly(riskFactor);

        assertThat(response.reasoning())
                .isEqualTo("백테스트 결과 변동성이 확인되었습니다.");

        assertThat(response.recommendations())
                .containsExactly("손실 한도를 낮추는 것을 고려하세요.");

        assertThat(response.failureType())
                .isNull();

        assertThat(response.message())
                .isEqualTo("AI 분석이 완료되었습니다.");

        verify(queryRepository)
                .findByIdAndUserId(analysisId, userId);
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

    @Test
    @DisplayName("실패한 AI 위험 분석 조회 시 실패 유형을 반환한다")
    void getAnalysis_failed() {
        AiRiskAnalysis analysis =
                AiRiskAnalysis.create(userId, strategyId, backtestId);

        analysis.fail(
                AiAnalysisFailureType.LLM_API_ERROR,
                "GPT API 호출 중 timeout 발생"
        );

        when(queryRepository.findByIdAndUserId(analysisId, userId))
                .thenReturn(Optional.of(analysis));

        AiRiskAnalysisDetailResponse response =
                queryService.getAnalysis(analysisId, userId);

        assertThat(response.status())
                .isEqualTo(AiAnalysisStatus.FAILED);

        assertThat(response.failureType())
                .isEqualTo(AiAnalysisFailureType.LLM_API_ERROR);
        assertThat(response.message())
                .isEqualTo("AI 분석 요청 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");

        verify(queryRepository)
                .findByIdAndUserId(analysisId, userId);
    }

    @Test
    @DisplayName("사용자의 AI 위험 분석 이력을 페이지 단위로 조회한다.")
    void getAnalysisHistory_success(){

        Pageable pageable = PageRequest.of(0, 10);

        AiRiskAnalysis firstAnalysis =
                AiRiskAnalysis.create(
                        userId,
                        strategyId,
                        backtestId
                );

        UUID secondStrategyId = UUID.randomUUID();
        UUID secondBacktestId = UUID.randomUUID();

        AiRiskAnalysis secondAnalysis =
                AiRiskAnalysis.create(
                        userId,
                        secondStrategyId,
                        secondBacktestId
                );

        Page<AiRiskAnalysis> analysisPage = new PageImpl<>(
                List.of(firstAnalysis, secondAnalysis),
                pageable,
                2
        );

        when(queryRepository.findAllByUserId(userId, pageable))
                .thenReturn(analysisPage);

        Page<AiRiskAnalysisHistoryItemResponse> response =
                queryService.getAnalysisHistory(userId, pageable);

        assertThat(response.getContent()).hasSize(2);
        assertThat(response.getTotalElements()).isEqualTo(2);
        assertThat(response.getNumber()).isZero();
        assertThat(response.getSize()).isEqualTo(10);

        assertThat(response.getContent().get(0).strategyId())
                .isEqualTo(strategyId);
        assertThat(response.getContent().get(0).backtestId())
                .isEqualTo(backtestId);
        assertThat(response.getContent().get(0).status())
                .isEqualTo(AiAnalysisStatus.PENDING);

        assertThat(response.getContent().get(1).strategyId())
                .isEqualTo(secondStrategyId);
        assertThat(response.getContent().get(1).backtestId())
                .isEqualTo(secondBacktestId);
        assertThat(response.getContent().get(1).status())
                .isEqualTo(AiAnalysisStatus.PENDING);

        verify(queryRepository)
                .findAllByUserId(userId, pageable);
    }

    @Test
    @DisplayName("다른 사용자의 AI 위험 분석 결과는 조회할 수 없다")
    void getAnalysis_otherUser_notFound() {
        UUID otherUserId = UUID.randomUUID();

        when(queryRepository.findByIdAndUserId(analysisId, otherUserId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                queryService.getAnalysis(analysisId, otherUserId)
        )
                .isInstanceOf(BusinessException.class);

        verify(queryRepository)
                .findByIdAndUserId(analysisId, otherUserId);
    }

    @Test
    @DisplayName("AI 위험 분석 이력이 없으면 빈 페이지를 반환한다.")
    void getAnalysisHistory_empty(){
        Pageable pageable = PageRequest.of(0, 10);

        when(queryRepository.findAllByUserId(userId, pageable))
                .thenReturn(Page.empty(pageable));

        Page<AiRiskAnalysisHistoryItemResponse> response =
                queryService.getAnalysisHistory(userId, pageable);

        assertThat(response.getContent()).isEmpty();
        assertThat(response.getTotalElements()).isZero();
        assertThat(response.getNumber()).isZero();
        assertThat(response.getSize()).isEqualTo(10);

        verify(queryRepository)
                .findAllByUserId(userId, pageable);
    }

    @Test
    @DisplayName("AI 위험 분석 실패 이력을 전체 조회한다.")
    void getFailureHistory_success() {
        Pageable pageable = PageRequest.of(0, 10);

        AiRiskAnalysis analysis1 =
                AiRiskAnalysis.create(userId, strategyId, backtestId);
        analysis1.fail(
                AiAnalysisFailureType.LLM_API_ERROR,
                "LLM API 호출 실패"
        );

        AiRiskAnalysis analysis2 =
                AiRiskAnalysis.create(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID()
                );
        analysis2.fail(
                AiAnalysisFailureType.RESPONSE_PARSE_ERROR,
                "응답 파싱 실패"
        );

        Page<AiRiskAnalysis> analyses =
                new PageImpl<>(
                        List.of(analysis1, analysis2),
                        pageable,
                        2
                );

        when(queryRepository.findAllByStatus(
                AiAnalysisStatus.FAILED,
                pageable
        )).thenReturn(analyses);

        Page<AiRiskAnalysisFailureHistoryItemResponse> response =
                queryService.getFailureHistory(null, pageable);

        assertThat(response.getContent()).hasSize(2);
        assertThat(response.getTotalElements()).isEqualTo(2);

        assertThat(response.getContent().get(0).failureType())
                .isEqualTo(AiAnalysisFailureType.LLM_API_ERROR);
        assertThat(response.getContent().get(0).failureMessage())
                .isEqualTo("LLM API 호출 실패");

        assertThat(response.getContent().get(1).failureType())
                .isEqualTo(AiAnalysisFailureType.RESPONSE_PARSE_ERROR);
        assertThat(response.getContent().get(1).failureMessage())
                .isEqualTo("응답 파싱 실패");

        verify(queryRepository)
                .findAllByStatus(AiAnalysisStatus.FAILED, pageable);
    }

    @Test
    @DisplayName("실패 유형으로 AI 위험 분석 실패 이력을 조회한다.")
    void getFailureHistory_withFailureType() {
        Pageable pageable = PageRequest.of(0, 10);
        AiAnalysisFailureType failureType =
                AiAnalysisFailureType.LLM_API_ERROR;

        AiRiskAnalysis analysis =
                AiRiskAnalysis.create(userId, strategyId, backtestId);
        analysis.fail(
                failureType,
                "LLM API 호출 실패"
        );

        Page<AiRiskAnalysis> analyses =
                new PageImpl<>(
                        List.of(analysis),
                        pageable,
                        1
                );

        when(queryRepository.findAllByStatusAndFailureType(
                AiAnalysisStatus.FAILED,
                failureType,
                pageable
        )).thenReturn(analyses);

        Page<AiRiskAnalysisFailureHistoryItemResponse> response =
                queryService.getFailureHistory(failureType, pageable);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getTotalElements()).isEqualTo(1);

        AiRiskAnalysisFailureHistoryItemResponse result =
                response.getContent().get(0);

        assertThat(result.analysisId()).isEqualTo(analysis.getId());
        assertThat(result.failureType()).isEqualTo(failureType);
        assertThat(result.failureMessage()).isEqualTo("LLM API 호출 실패");

        verify(queryRepository)
                .findAllByStatusAndFailureType(
                        AiAnalysisStatus.FAILED,
                        failureType,
                        pageable
                );
    }

    @Test
    @DisplayName("AI 위험 분석 실패 이력이 없으면 빈 페이지를 반환한다.")
    void getFailureHistory_empty() {
        Pageable pageable = PageRequest.of(0, 10);

        when(queryRepository.findAllByStatus(
                AiAnalysisStatus.FAILED,
                pageable
        )).thenReturn(Page.empty(pageable));

        Page<AiRiskAnalysisFailureHistoryItemResponse> response =
                queryService.getFailureHistory(null, pageable);

        assertThat(response.getContent()).isEmpty();
        assertThat(response.getTotalElements()).isZero();
        assertThat(response.getNumber()).isZero();
        assertThat(response.getSize()).isEqualTo(10);

        verify(queryRepository)
                .findAllByStatus(AiAnalysisStatus.FAILED, pageable);
    }
}