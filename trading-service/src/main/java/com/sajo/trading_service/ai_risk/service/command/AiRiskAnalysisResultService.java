package com.sajo.trading_service.ai_risk.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.ai_risk.domain.AiAnalysisFailureType;
import com.sajo.trading_service.ai_risk.domain.AiRiskAnalysis;
import com.sajo.trading_service.ai_risk.domain.RiskFactor;
import com.sajo.trading_service.ai_risk.domain.RiskLevel;
import com.sajo.trading_service.ai_risk.exception.AiRiskErrorCode;
import com.sajo.trading_service.ai_risk.repository.command.AiRiskAnalysisCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiRiskAnalysisResultService {

    private final AiRiskAnalysisCommandRepository aiRiskAnalysisCommandRepository;

    @Transactional
    public void complete(
            UUID analysisId,
            RiskLevel riskLevel,
            String summary,
            List<RiskFactor> riskFactors,
            String reasoning,
            List<String> recommendations
    ){
        AiRiskAnalysis analysis = aiRiskAnalysisCommandRepository.findById(analysisId)
                .orElseThrow(() -> new BusinessException(AiRiskErrorCode.ANALYSIS_NOT_FOUND));

        analysis.complete(
                riskLevel,
                summary,
                riskFactors,
                reasoning,
                recommendations
        );
    }

    @Transactional
    public void fail(
            UUID analysisId,
            AiAnalysisFailureType failureType,
            String failureMessage
    ){
        AiRiskAnalysis analysis = aiRiskAnalysisCommandRepository.findById(analysisId)
                .orElseThrow(() -> new BusinessException(AiRiskErrorCode.ANALYSIS_NOT_FOUND));

        analysis.fail(
                failureType,
                failureMessage
        );
    }
}
