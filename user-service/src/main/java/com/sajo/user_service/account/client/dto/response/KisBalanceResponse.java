package com.sajo.user_service.account.client.dto.response;

import java.util.List;

// KIS 주식잔고조회(inquire-balance) 응답 - rt_cd "0"이 아니면 업무상 실패 (HTTP 200이어도)
public record KisBalanceResponse(
        String rt_cd,
        String msg_cd,
        String msg1,
        String ctx_area_fk100,
        String ctx_area_nk100,
        List<KisBalanceHoldingResponse> output1,
        List<KisBalanceSummaryResponse> output2
) {
}
