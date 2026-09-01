package com.sajo.market_service.market.client.user.dto;

/**
 * User Service가 관리하는 KIS 인증정보 계약이다.
 * 인증정보는 Market Service에 저장하지 않고 KIS 호출 시점에만 사용한다.
 */
public record UserKisTokenResponse(
        String accessToken,
        String appKey,
        String secretKey
) {
}
