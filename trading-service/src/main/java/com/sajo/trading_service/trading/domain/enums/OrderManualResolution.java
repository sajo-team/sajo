package com.sajo.trading_service.trading.domain.enums;

public enum OrderManualResolution {
    FAILED, // KIS 확인 결과 주문 자체가 없음 / 실패 확인
    CANCELED, // KIS에서 취소된 주문임을 확인
    ACCEPTED // KIS에 정상 접수된 주문임을 확인
            //→ 이후 기존 체결 추적 계속
}