package com.sajo.user_service.account.cache;

// Redis 자체 장애(연결 실패 등)를 나타내는 내부 신호용 예외.
// KisTokenRemoteCache가 DataAccessException을 잡아 이 예외로 바꿔 던지고,
// KisTokenCacheQueryService가 한 곳에서만 잡아 fail-open(KIS 직접 호출) 여부를 판단한다.
// 비즈니스 규칙 위반이 아니라 인프라 장애 신호라 BusinessException을 상속하지 않는다 -
// 어디서도 이 예외 그대로 HTTP 응답까지 전파돼서는 안 된다.
public class RedisUnavailableException extends RuntimeException {

    public RedisUnavailableException(Throwable cause) {
        super(cause);
    }
}
