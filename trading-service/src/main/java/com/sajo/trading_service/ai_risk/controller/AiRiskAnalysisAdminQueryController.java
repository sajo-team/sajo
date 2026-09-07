package com.sajo.trading_service.ai_risk.controller;
 
import com.sajo.common.code.GeneralResponseCode;
import com.sajo.common.response.GeneralResponse;
import com.sajo.common.response.PageResponse;
import com.sajo.trading_service.ai_risk.controller.dto.response.AiAnalysisAuditDetailResponse;
import com.sajo.trading_service.ai_risk.controller.dto.response.AiRiskAnalysisFailureHistoryItemResponse;
import com.sajo.trading_service.ai_risk.domain.AiAnalysisFailureType;
import com.sajo.trading_service.ai_risk.service.query.AiAnalysisHistoryQueryService;
import com.sajo.trading_service.ai_risk.service.query.AiRiskAnalysisQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/ai/analyses")
public class AiRiskAnalysisAdminQueryController {
 
    private final AiRiskAnalysisQueryService aiRiskAnalysisQueryService;
    private final AiAnalysisHistoryQueryService aiAnalysisHistoryQueryService;
 
    @GetMapping("/failures")
    public ResponseEntity<GeneralResponse<PageResponse<AiRiskAnalysisFailureHistoryItemResponse>>> getFailureHistory(
            @RequestHeader("X-User-Role") String role,
            @RequestParam(required = false) AiAnalysisFailureType failureType,
            Pageable pageable
            ) {
        // Gateway가 검증한 X-User-Role만 신뢰한다 (Principal/@PreAuthorize 기반 검증은
        // 별도 인프라 작업 - 그 전까지는 헤더 값을 직접 확인)
        if (!"ADMIN".equals(role)) {
            throw new AccessDeniedException("관리자 권한이 필요합니다");
        }
 
        Page<AiRiskAnalysisFailureHistoryItemResponse> page = aiRiskAnalysisQueryService.getFailureHistory(failureType, pageable);
 
        PageResponse<AiRiskAnalysisFailureHistoryItemResponse> response = PageResponse.from(page);
 
        return GeneralResponse.toResponseEntity(
                GeneralResponseCode.OK,
                response
        );
    }

    @GetMapping("/{analysisId}/audit")
    public ResponseEntity<GeneralResponse<AiAnalysisAuditDetailResponse>> getAuditDetail(
            @RequestHeader("X-User-Role") String role,
            @PathVariable UUID analysisId
            ){
        if (!"ADMIN".equals(role)) {
            throw new AccessDeniedException("관리자 권한이 필요합니다");
        }

        AiAnalysisAuditDetailResponse response = aiAnalysisHistoryQueryService.getAuditDetail(analysisId);

        return GeneralResponse.toResponseEntity(
                GeneralResponseCode.OK,
                response
        );
    }
}
