package com.sajo.trading_service.ai_risk.service.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.ai_risk.controller.dto.response.AiRiskAnalysisDetailResponse;
import com.sajo.trading_service.ai_risk.domain.AiRiskAnalysis;
import com.sajo.trading_service.ai_risk.exception.AiRiskErrorCode;
import com.sajo.trading_service.ai_risk.repository.query.AiRiskAnalysisQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiRiskAnalysisQueryService {

    private final AiRiskAnalysisQueryRepository queryRepository;

    public AiRiskAnalysisDetailResponse getAnalysis(
            UUID analysisId,
            UUID userId
    ){
        AiRiskAnalysis analysis = queryRepository.findByIdAndUserId(analysisId, userId)
                .orElseThrow(() -> new BusinessException(AiRiskErrorCode.ANALYSIS_NOT_FOUND));

        return AiRiskAnalysisDetailResponse.from(analysis);
    }
}
