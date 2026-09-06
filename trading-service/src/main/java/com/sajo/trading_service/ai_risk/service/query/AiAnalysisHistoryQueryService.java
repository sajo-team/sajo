package com.sajo.trading_service.ai_risk.service.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.trading_service.ai_risk.controller.dto.response.AiAnalysisAuditDetailResponse;
import com.sajo.trading_service.ai_risk.document.AiAnalysisHistory;
import com.sajo.trading_service.ai_risk.exception.AiRiskErrorCode;
import com.sajo.trading_service.ai_risk.repository.query.AiAnalysisHistoryQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiAnalysisHistoryQueryService {

    private final AiAnalysisHistoryQueryRepository aiAnalysisHistoryQueryRepository;

    public AiAnalysisAuditDetailResponse getAuditDetail(UUID analysisId){
        AiAnalysisHistory history = aiAnalysisHistoryQueryRepository.findByAnalysisId(analysisId)
                .orElseThrow(()-> new BusinessException(AiRiskErrorCode.AUDIT_HISTORY_NOT_FOUND));

        return AiAnalysisAuditDetailResponse.from(history);
    }
}
