package com.sajo.trading_service.ai_risk.controller;

import com.sajo.common.code.GeneralResponseCode;
import com.sajo.common.response.GeneralResponse;
import com.sajo.trading_service.ai_risk.controller.dto.request.AiRiskAnalysisCreateRequest;
import com.sajo.trading_service.ai_risk.controller.dto.response.AiRiskAnalysisCreateResponse;
import com.sajo.trading_service.ai_risk.service.command.AiRiskAnalysisCommandService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/ai/analyses")
public class AiRiskAnalysisCommandController {

    private final AiRiskAnalysisCommandService aiRiskAnalysisCommandService;

    @PostMapping
    public ResponseEntity<GeneralResponse<AiRiskAnalysisCreateResponse>> createAnalysis(
            @RequestParam("userId")UUID userId, //TODO: Gateway에서 JWT 검증 후 전달하는 X-User-Id 헤더를 사용하도록 변경
            @Valid @RequestBody AiRiskAnalysisCreateRequest request
            ){
        AiRiskAnalysisCreateResponse response = aiRiskAnalysisCommandService.create(userId, request);

        return GeneralResponse.toResponseEntity(
                GeneralResponseCode.CREATED,
                response
        );
    }
}
