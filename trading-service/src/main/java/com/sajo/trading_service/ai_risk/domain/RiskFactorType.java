package com.sajo.trading_service.ai_risk.domain;

public enum RiskFactorType {
    MAX_DRAWDOWN, //최대 낙폭이 커 손실 폭이 큼
    CONSECUTIVE_LOSS, //손실이 연속적으로 발생할 위험
    LOW_WIN_RATE, //승률이 낮음
    LOW_TRADE_COUNT, //거래 표본이 적어 백테스트 신뢰도가 낮음
    STOP_LOSS_RISK, //손실 조건으로 인한 손실 위험
    FINANCIAL_INDICATOR_RISK //재무지표 조건에서 확인되는 위험
}
