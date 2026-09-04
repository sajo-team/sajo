package com.sajo.user_service.account.client;

// KIS 연속조회 결과 - 응답 헤더의 tr_cont(F/M=더 있음, D/E=마지막)를 hasNext로 변환해서 body와 함께 반환한다
public record KisContinuationResult<T>(T body, boolean hasNext) {
}
