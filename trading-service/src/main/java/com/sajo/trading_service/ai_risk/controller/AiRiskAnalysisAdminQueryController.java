package com.sajo.trading_service.ai_risk.controller;

import com.sajo.common.code.GeneralResponseCode;
import com.sajo.common.response.GeneralResponse;
import com.sajo.common.response.PageResponse;
import com.sajo.trading_service.ai_risk.controller.dto.response.AiRiskAnalysisFailureHistoryItemResponse;
import com.sajo.trading_service.ai_risk.domain.AiAnalysisFailureType;
import com.sajo.trading_service.ai_risk.service.query.AiRiskAnalysisQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/ai/analyses")
public class AiRiskAnalysisAdminQueryController {

    private final AiRiskAnalysisQueryService aiRiskAnalysisQueryService;

    // TODO Security 적용 후 관리자 권한 검증
    @GetMapping("/failures")
    public ResponseEntity<GeneralResponse<PageResponse<AiRiskAnalysisFailureHistoryItemResponse>>> getFailureHistory(
            @RequestParam(required = false) AiAnalysisFailureType failureType,
            Pageable pageable
            ) {
        Page<AiRiskAnalysisFailureHistoryItemResponse> page = aiRiskAnalysisQueryService.getFailureHistory(failureType, pageable);

        PageResponse<AiRiskAnalysisFailureHistoryItemResponse> response = PageResponse.from(page);

        return GeneralResponse.toResponseEntity(
                GeneralResponseCode.OK,
                response
        );
    }
}
