package com.sajo.trading_service.ai_risk.controller;

import com.sajo.common.code.GeneralResponseCode;
import com.sajo.common.response.GeneralResponse;
import com.sajo.trading_service.ai_risk.controller.dto.request.AiPromptVersionCreateRequest;
import com.sajo.trading_service.ai_risk.controller.dto.response.AiPromptVersionCreateResponse;
import com.sajo.trading_service.ai_risk.service.command.AiPromptVersionCommandService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/ai/prompt-versions")
public class AiPromptVersionCommandController {

    private final AiPromptVersionCommandService promptVersionCommandService;

    // TODO: 공통 Security 적용 시 관리자 권한 검증
    @PostMapping
    public ResponseEntity<GeneralResponse<AiPromptVersionCreateResponse>> create(
            @Valid @RequestBody AiPromptVersionCreateRequest request
            ){
        AiPromptVersionCreateResponse response = promptVersionCommandService.create(request);

        return GeneralResponse.toResponseEntity(
                GeneralResponseCode.CREATED,
                response
        );
    }
}
