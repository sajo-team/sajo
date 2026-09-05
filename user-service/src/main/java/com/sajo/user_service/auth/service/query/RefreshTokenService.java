package com.sajo.user_service.auth.service.query;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

// Refresh Token 발급/검증/회전을 Redis로 관리한다.
//
// 저장 스키마:
//   refresh-token:{token}    -> userId  (TTL 14일, 발급 시점 기준)
//   refresh-current:{userId} -> 현재 유효한 token 값 (TTL 14일, 회전마다 갱신)
//
// 회전(rotation) + 재사용 탐지: refresh 요청마다 새 토큰을 발급하고, 쓰인 토큰은
// "현재 유효한 토큰"에서 밀려난다. 이미 회전되어 밀려난(=한 번 쓰인) 토큰이 다시
// 제시되면 탈취로 간주해 해당 사용자의 세션을 통째로 무효화한다 - 그 순간 요청을
// 보낸 쪽이 진짜 사용자인지 탈취범인지 서버가 구분할 수 없으므로, 이미 재발급된
// "현재" 토큰까지 같이 죽여야 탈취범이 들고 있는 새 토큰도 무효화된다.
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final String TOKEN_KEY_PREFIX = "user-service:refresh-token:";
    private static final String CURRENT_KEY_PREFIX = "user-service:refresh-current:";
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(14);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final StringRedisTemplate stringRedisTemplate;

    // 로그인 시 새 refresh token을 발급한다. fail-open - Redis 장애 시 로그인 자체를
    // 막지 않기 위해 빈 Optional을 반환한다(access token만 발급되고, 클라이언트는
    // 그게 만료되면 재로그인해야 한다 - 로그인 자체가 막히는 것보다는 낫다).
    public Optional<String> issue(UUID userId) {
        try {
            String token = generateToken();
            String userIdValue = userId.toString();
            stringRedisTemplate.opsForValue().set(TOKEN_KEY_PREFIX + token, userIdValue, REFRESH_TOKEN_TTL);
            stringRedisTemplate.opsForValue().set(CURRENT_KEY_PREFIX + userIdValue, token, REFRESH_TOKEN_TTL);
            return Optional.of(token);
        } catch (RuntimeException e) {
            log.warn("Redis 기록 실패로 refresh token을 발급하지 못함(fail-open) - access token만 발급됨", e);
            return Optional.empty();
        }
    }

    // 제시된 refresh token을 검증하고 회전시킨다. 유효하지 않거나 재사용이 감지되면
    // 빈 Optional을 반환한다(호출부에서 INVALID_REFRESH_TOKEN으로 매핑).
    //
    // 이 메서드는 fail-open하지 않는다 - LoginAttemptService의 fail-open과 달리,
    // 여기서는 Redis 자체가 "토큰이 유효한지"를 판단하는 유일한 근거이기 때문에
    // 확인이 안 되는 상태에서 새 토큰을 내주는 건 안전하지 않다. Redis 장애 시에는
    // 예외가 그대로 전파되어 500으로 응답한다 - 클라이언트는 access token이 아직
    // 유효하면 그걸 계속 쓰거나, 잠시 후 재시도하면 된다.
    public Optional<RotationResult> rotate(String presentedToken) {
        String userIdValue = stringRedisTemplate.opsForValue().get(TOKEN_KEY_PREFIX + presentedToken);
        if (userIdValue == null) {
            return Optional.empty();
        }

        String currentToken = stringRedisTemplate.opsForValue().get(CURRENT_KEY_PREFIX + userIdValue);
        if (currentToken == null || !currentToken.equals(presentedToken)) {
            // 이미 회전되어 밀려난 토큰이 다시 제시됨 - 탈취 의심, 세션 전체 무효화
            log.warn("Refresh Token 재사용 감지 - userId={} 세션 전체 무효화", userIdValue);
            stringRedisTemplate.delete(CURRENT_KEY_PREFIX + userIdValue);
            return Optional.empty();
        }

        UUID userId = UUID.fromString(userIdValue);
        String newToken = generateToken();
        stringRedisTemplate.opsForValue().set(TOKEN_KEY_PREFIX + newToken, userIdValue, REFRESH_TOKEN_TTL);
        stringRedisTemplate.opsForValue().set(CURRENT_KEY_PREFIX + userIdValue, newToken, REFRESH_TOKEN_TTL);
        return Optional.of(new RotationResult(userId, newToken));
    }

    // 로그아웃 시 현재 유효한 refresh token을 무효화한다. Redis 장애로 실패해도
    // 로그아웃 자체는 클라이언트 입장에서 성공한 것처럼 처리되는 게 맞다(최악의
    // 경우 기존 토큰이 좀 더 오래 살아있는 것뿐, 보안 우회로 이어지진 않는다).
    public void revoke(UUID userId) {
        try {
            stringRedisTemplate.delete(CURRENT_KEY_PREFIX + userId.toString());
        } catch (RuntimeException e) {
            log.warn("Redis 기록 실패로 로그아웃 시 refresh token을 무효화하지 못함", e);
        }
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record RotationResult(UUID userId, String newRefreshToken) {
    }
}
