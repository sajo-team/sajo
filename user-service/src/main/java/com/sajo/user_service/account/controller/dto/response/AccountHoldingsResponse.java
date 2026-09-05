package com.sajo.user_service.account.controller.dto.response;

import com.sajo.user_service.account.client.dto.response.KisBalanceHoldingResponse;

import java.time.Instant;
import java.util.List;

public record AccountHoldingsResponse(
        List<AccountHoldingResponse> holdings, // 보유종목 리스트
        boolean hasNext, // 다음 페이지 존재 여부
        String nextCtxAreaFk100, // 다음 요청에 그대로 넘길 값 - hasNext가 false면 null
        String nextCtxAreaNk100, // 다음 요청에 그대로 넘길 값 - hasNext가 false면 null
        Instant asOf // 조회 기준 시각 (KIS 응답에는 없음 - 조회 시점에 직접 채움)
) {
    public static AccountHoldingsResponse from(
            List<KisBalanceHoldingResponse> output1, boolean hasNext,
            String nextCtxAreaFk100, String nextCtxAreaNk100, Instant asOf
    ) {
        List<AccountHoldingResponse> holdings = output1.stream()
                .map(AccountHoldingResponse::from)
                .toList();
        return new AccountHoldingsResponse(holdings, hasNext, nextCtxAreaFk100, nextCtxAreaNk100, asOf);
    }
}
