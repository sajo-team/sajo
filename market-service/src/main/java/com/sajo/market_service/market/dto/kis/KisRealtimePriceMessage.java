package com.sajo.market_service.market.dto.kis;

/**
 * KIS WebSocket 실시간 체결가(H0STCNT0) 원문 레코드 하나를 표현한다.
 *
 * <p>원문은 {@code 암호화구분|tr_id|데이터건수|필드1^필드2^...^필드N(^필드1^필드2^...^필드N 반복)} 형태이며,
 * 레코드 하나는 {@value #FIELD_COUNT_PER_RECORD}개의 {@code ^} 구분 필드로 이루어진다. 이 상수는 실제
 * 운영 환경에서 캡처한 원문 샘플(2026-09-14, 005930/000660 체결가)을 기준으로 정했다 — KIS 공식 문서에
 * 직접 접근할 수 없는 환경에서 만들었으므로, 필드 순서가 바뀌는 KIS 측 변경이 있었는지는 운영 중 실제
 * 파싱 실패율(로그)로 주기적으로 재확인하는 것을 권장한다.</p>
 */
public record KisRealtimePriceMessage(
        String stockCode,
        String tradeTime,
        String currentPrice,
        String changeSign,
        String changePrice,
        String changeRate,
        String openPrice,
        String highPrice,
        String lowPrice,
        String tradeVolume,
        String accumulatedVolume,
        String accumulatedTradeAmount,
        String businessDate
) {

    /** 실측 원문 기준 레코드당 총 필드 수(레코드 경계 판별용). 사용하지 않는 뒤쪽 필드까지 포함한 개수다. */
    public static final int FIELD_COUNT_PER_RECORD = 47;

    private static final int IDX_STOCK_CODE = 0;
    private static final int IDX_TRADE_TIME = 1;
    private static final int IDX_CURRENT_PRICE = 2;
    private static final int IDX_CHANGE_SIGN = 3;
    private static final int IDX_CHANGE_PRICE = 4;
    private static final int IDX_CHANGE_RATE = 5;
    private static final int IDX_OPEN_PRICE = 7;
    private static final int IDX_HIGH_PRICE = 8;
    private static final int IDX_LOW_PRICE = 9;
    private static final int IDX_TRADE_VOLUME = 12;
    private static final int IDX_ACCUMULATED_VOLUME = 13;
    private static final int IDX_ACCUMULATED_TRADE_AMOUNT = 14;
    private static final int IDX_BUSINESS_DATE = 33;

    /** fields는 정확히 {@link #FIELD_COUNT_PER_RECORD}개 길이여야 한다. */
    public static KisRealtimePriceMessage fromFields(String[] fields) {
        return new KisRealtimePriceMessage(
                fields[IDX_STOCK_CODE],
                fields[IDX_TRADE_TIME],
                fields[IDX_CURRENT_PRICE],
                fields[IDX_CHANGE_SIGN],
                fields[IDX_CHANGE_PRICE],
                fields[IDX_CHANGE_RATE],
                fields[IDX_OPEN_PRICE],
                fields[IDX_HIGH_PRICE],
                fields[IDX_LOW_PRICE],
                fields[IDX_TRADE_VOLUME],
                fields[IDX_ACCUMULATED_VOLUME],
                fields[IDX_ACCUMULATED_TRADE_AMOUNT],
                fields[IDX_BUSINESS_DATE]
        );
    }
}
