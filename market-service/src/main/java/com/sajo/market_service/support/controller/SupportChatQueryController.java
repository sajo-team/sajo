package com.sajo.market_service.support.controller;

import com.sajo.common.code.GeneralResponseCode;
import com.sajo.common.response.GeneralResponse;
import com.sajo.market_service.support.controller.dto.request.SupportAskRequest;
import com.sajo.market_service.support.controller.dto.response.SupportAskResponse;
import com.sajo.market_service.support.service.query.SupportChatQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * RAG(검색 증강 생성) 기반 지능형 고객 응대 챗봇 (도전 과제).
 *
 * <p>다른 도메인의 조회 전용 API와 동일하게 Command/Query 네이밍 컨벤션을 따라
 * {@code SupportChatQueryController} → {@code SupportChatQueryService}로 명명했다
 * (질문에 대해 상태를 변경하지 않고 답변만 조회/생성하므로 Query에 해당한다).</p>
 *
 * <p>{@code X-User-Id}를 다른 사용자용 컨트롤러와 동일하게 필수로 요구한다. 이 API는
 * 호출마다 OpenAI 임베딩/채팅 완성 비용이 발생하므로, Gateway의 {@code JwtAuthenticationFilter}를
 * 반드시 거치도록(=로그인된 사용자만 호출 가능하도록) 강제해 비인증 호출로 인한 비용 남용을
 * 막는다. 실제 응답 내용에는 사용자별 데이터를 사용하지 않지만, 감사/추적을 위해 서비스
 * 계층에서 로깅한다.</p>
 */
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/support")
@ConditionalOnProperty(prefix = "sajo.support.rag", name = "enabled", havingValue = "true")
public class SupportChatQueryController {

    private final SupportChatQueryService supportChatQueryService;

    @PostMapping("/ask")
    public ResponseEntity<GeneralResponse<SupportAskResponse>> ask(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody SupportAskRequest request
    ) {
        SupportAskResponse response = supportChatQueryService.ask(userId, request.question());
        return GeneralResponse.toResponseEntity(GeneralResponseCode.OK, response);
    }
}
