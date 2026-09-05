package com.sajo.user_service.auth.controller.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshRequest(

        @NotBlank
        // permitAll(인증 없이 호출 가능)인 엔드포인트라 임의로 긴 문자열을 반복적으로
        // 보내는 요청에 대한 최소한의 방어선으로 길이를 제한한다. 실제 발급되는 토큰은
        // 43자(32바이트를 base64url로 인코딩) 고정이라 512자면 충분히 여유 있다.
        @Size(max = 512)
        String refreshToken
) {
}
