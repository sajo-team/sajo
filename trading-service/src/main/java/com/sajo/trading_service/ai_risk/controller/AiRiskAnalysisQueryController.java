package com.sajo.trading_service.ai_risk.controller;

import com.sajo.common.code.GeneralResponseCode;
import com.sajo.common.response.GeneralResponse;
import com.sajo.trading_service.ai_risk.controller.dto.response.AiRiskAnalysisDetailResponse;
import com.sajo.trading_service.ai_risk.service.query.AiRiskAnalysisQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/ai/analyses")
public class AiRiskAnalysisQueryController {

    private final AiRiskAnalysisQueryService aiRiskAnalysisQueryService;

    @GetMapping("/{analysisId}")
    public ResponseEntity<GeneralResponse<AiRiskAnalysisDetailResponse>> getAnalysis(
            @RequestParam("userId") UUID userId, // TODO: Gateway에서 전달하는 X-User-Id 헤더로 변경
            @PathVariable UUID analysisId
    ){
        AiRiskAnalysisDetailResponse response = aiRiskAnalysisQueryService.getAnalysis(analysisId, userId);

        return GeneralResponse.toResponseEntity(
                GeneralResponseCode.OK,
                response
        );
    }
}
