package com.sajo.trading_service.ai_risk.controller;
 
import com.sajo.common.code.GeneralResponseCode;
import com.sajo.common.response.GeneralResponse;
import com.sajo.common.response.PageResponse;
import com.sajo.trading_service.ai_risk.controller.dto.response.AiRiskAnalysisDetailResponse;
import com.sajo.trading_service.ai_risk.controller.dto.response.AiRiskAnalysisHistoryItemResponse;
import com.sajo.trading_service.ai_risk.service.query.AiRiskAnalysisQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID analysisId
    ){
        AiRiskAnalysisDetailResponse response = aiRiskAnalysisQueryService.getAnalysis(analysisId, userId);
 
        return GeneralResponse.toResponseEntity(
                GeneralResponseCode.OK,
                response
        );
    }
 
    @GetMapping
    public ResponseEntity<GeneralResponse<PageResponse<AiRiskAnalysisHistoryItemResponse>>> getAnalysisHistory(
            @RequestHeader("X-User-Id") UUID userId,
            Pageable pageable
    ) {
        Page<AiRiskAnalysisHistoryItemResponse> page =
                aiRiskAnalysisQueryService.getAnalysisHistory(
                        userId,
                        pageable
                );
 
        PageResponse<AiRiskAnalysisHistoryItemResponse> response = PageResponse.from(page);
 
        return GeneralResponse.toResponseEntity(
                GeneralResponseCode.OK,
                response
        );
    }
}
