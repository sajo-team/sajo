package com.sajo.market_service.support.controller;

import com.sajo.common.code.GeneralResponseCode;
import com.sajo.common.response.GeneralResponse;
import com.sajo.market_service.support.dto.request.SupportAskRequest;
import com.sajo.market_service.support.dto.response.SupportAskResponse;
import com.sajo.market_service.support.service.SupportChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * RAG(검색 증강 생성) 기반 지능형 고객 응대 챗봇 (도전 과제).
 */
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/support")
public class SupportChatController {

    private final SupportChatService supportChatService;

    @PostMapping("/ask")
    public ResponseEntity<GeneralResponse<SupportAskResponse>> ask(
            @Valid @RequestBody SupportAskRequest request
    ) {
        SupportAskResponse response = supportChatService.ask(request.question());
        return GeneralResponse.toResponseEntity(GeneralResponseCode.OK, response);
    }
}
