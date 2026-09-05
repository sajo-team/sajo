package com.sajo.trading_service.ai_risk.controller;
 
import com.sajo.common.code.GeneralResponseCode;
import com.sajo.common.response.GeneralResponse;
import com.sajo.trading_service.ai_risk.controller.dto.request.AiPromptVersionCreateRequest;
import com.sajo.trading_service.ai_risk.controller.dto.response.AiPromptVersionCreateResponse;
import com.sajo.trading_service.ai_risk.service.command.AiPromptVersionCommandService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
 
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/ai/prompt-versions")
public class AiPromptVersionCommandController {
 
    private final AiPromptVersionCommandService promptVersionCommandService;
 
    @PostMapping
    public ResponseEntity<GeneralResponse<AiPromptVersionCreateResponse>> create(
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody AiPromptVersionCreateRequest request
            ){
        // Gateway가 검증한 X-User-Role만 신뢰한다 (Principal/@PreAuthorize 기반 검증은
        // 별도 인프라 작업 - 그 전까지는 헤더 값을 직접 확인)
        if (!"ADMIN".equals(role)) {
            throw new AccessDeniedException("관리자 권한이 필요합니다");
        }
 
        AiPromptVersionCreateResponse response = promptVersionCommandService.create(request);
 
        return GeneralResponse.toResponseEntity(
                GeneralResponseCode.CREATED,
                response
        );
    }
}
