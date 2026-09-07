package com.sajo.trading_service.ai_risk.controller;

import com.sajo.common.code.GeneralResponseCode;
import com.sajo.common.response.GeneralResponse;
import com.sajo.common.response.PageResponse;
import com.sajo.trading_service.ai_risk.controller.dto.response.AiPromptVersionHistoryResponse;
import com.sajo.trading_service.ai_risk.service.query.AiPromptVersionQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/ai/prompt-versions")
public class AiPromptVersionQueryController {

    private final AiPromptVersionQueryService aiPromptVersionQueryService;

    @GetMapping
    public ResponseEntity<GeneralResponse<PageResponse<AiPromptVersionHistoryResponse>>> getPromptVersions(
            @RequestHeader("X-User-Role") String role,
            Pageable pageable
    ){
        if (!"ADMIN".equals(role)) {
            throw new AccessDeniedException("관리자 권한이 필요합니다");
        }

        Page<AiPromptVersionHistoryResponse> page =
                aiPromptVersionQueryService.getPromptVersionHistories(pageable);

        PageResponse<AiPromptVersionHistoryResponse> response =
                PageResponse.from(page);

        return GeneralResponse.toResponseEntity(
                GeneralResponseCode.OK,
                response
        );
    }
}
