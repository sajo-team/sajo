package com.sajo.user_service.account.client.dto.response;

// kis tr(잔고조회 등 거래 조회) 에러 응답 - KisBalanceResponse와 동일한 rt_cd/msg_cd/msg1 포맷
public record KisTrErrorResponse(
        String rt_cd,
        String msg_cd,
        String msg1
) implements KisErrorInfo {

    @Override
    public String code() {
        return msg_cd;
    }

    @Override
    public String message() {
        return msg1;
    }
}
