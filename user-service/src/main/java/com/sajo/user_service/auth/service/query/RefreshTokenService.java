package com.sajo.user_service.auth.service.query;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Refresh Token 발급/검증/회전을 Redis로 관리한다.
//
// 다중 기기(세션) 로그인 지원 - 리뷰 반영: 로그인마다 새로운 sessionId를 부여하고,
// "현재 유효한 토큰"을 사용자 단위가 아니라 세션 단위로 추적한다. 이렇게 해야
// 휴대폰/PC 등 여러 기기에서 동시에 로그인해도 서로의 세션을 침범하지 않는다 -
// 세션 단위 추적이 없으면, 한 기기가 로그인할 때마다 다른 기기의 "현재 토큰" 포인터를
// 덮어써서, 다른 기기가 정상적으로 refresh를 호출해도 재사용(탈취)으로 오탐되어
// 강제 로그아웃당하는 문제가 생긴다.
//
// 저장 스키마:
//   refresh-token:{token}      -> "{userId}|{sessionId}"  (TTL 14일, 발급 시점 기준)
//   refresh-current:{sessionId} -> 현재 유효한 token 값 (TTL 14일, 회전마다 갱신)
//
// 회전(rotation) + 재사용 탐지: refresh 요청마다 새 토큰을 발급하고, 쓰인 토큰은
// "그 세션의 현재 유효한 토큰"에서 밀려난다. 이미 회전되어 밀려난(=한 번 쓰인) 토큰이
// 다시 제시되면 탈취로 간주해 "그 세션만" 무효화한다 - 세션 단위로 추적하므로 다른
// 기기(세션)에는 영향이 없다.
//
// issue()/rotate() 둘 다 Lua 스크립트로 원자화한다: "현재 토큰과 비교 후 교체" 같은
// 검증→갱신을 별도의 Redis 호출로 나눠서 하면(check-then-act), 같은 토큰으로 거의
// 동시에 두 요청이 들어올 때(네트워크 재시도, 다중 탭 등) 둘 다 검증을 통과한 뒤
// 서로의 갱신을 덮어써서, 먼저 응답받은 클라이언트의 새 토큰이 곧바로 "밀려난" 상태가
// 되어 다음 refresh에서 재사용(탈취)으로 오탐될 수 있다. EVAL로 실행되는 Lua
// 스크립트는 그 안의 모든 명령을 하나의 원자적 연산으로 실행하므로 이 틈이 생기지 않는다.
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final String TOKEN_KEY_PREFIX = "user-service:refresh-token:";
    private static final String CURRENT_KEY_PREFIX = "user-service:refresh-current:";
    private static final String COMBINED_VALUE_SEPARATOR = "|";
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(14);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // KEYS[1] = TOKEN_KEY_PREFIX + token, KEYS[2] = CURRENT_KEY_PREFIX + sessionId
    // ARGV[1] = combined("userId|sessionId"), ARGV[2] = token, ARGV[3] = ttlSeconds
    private static final RedisScript<String> ISSUE_SCRIPT = RedisScript.of(
            "redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[3]) "
                    + "redis.call('SET', KEYS[2], ARGV[2], 'EX', ARGV[3]) "
                    + "return 'OK'",
            String.class
    );

    // KEYS[1] = TOKEN_KEY_PREFIX + presentedToken
    // ARGV[1] = presentedToken, ARGV[2] = newToken, ARGV[3] = ttlSeconds,
    // ARGV[4] = CURRENT_KEY_PREFIX, ARGV[5] = TOKEN_KEY_PREFIX, ARGV[6] = separator("|")
    //
    // 반환값은 항상 4개짜리 배열 {userId, sessionId, newTokenOrEmpty, status} - Lua에서
    // nil을 반환하는 경로를 아예 두지 않는다(Lua nil을 Java List로 변환할 때 겪었던
    // 문제를 피하기 위함).
    //   status = "UNKNOWN_TOKEN"   : 존재하지 않거나 이미 자연 만료된 토큰
    //   status = "NO_SESSION"      : 그 세션이 없음(로그아웃되었거나 이미 무효화된 상태) - 공격 아님
    //   status = "REUSE_DETECTED"  : 이미 회전되어 밀려난 토큰이 다시 제시됨 - 그 세션만 탈취 의심으로 무효화
    //   status = "OK"              : 정상 회전 성공
    private static final RedisScript<List> ROTATE_SCRIPT = RedisScript.of(
            "local combined = redis.call('GET', KEYS[1]) "
                    + "if not combined then return {'', '', '', 'UNKNOWN_TOKEN'} end "
                    + "local sepIndex = string.find(combined, ARGV[6], 1, true) "
                    + "local userId = string.sub(combined, 1, sepIndex - 1) "
                    + "local sessionId = string.sub(combined, sepIndex + 1) "
                    + "local currentKey = ARGV[4] .. sessionId "
                    + "local currentToken = redis.call('GET', currentKey) "
                    + "if not currentToken then "
                    + "  return {userId, sessionId, '', 'NO_SESSION'} "
                    + "end "
                    + "if currentToken ~= ARGV[1] then "
                    + "  redis.call('DEL', currentKey) "
                    + "  return {userId, sessionId, '', 'REUSE_DETECTED'} "
                    + "end "
                    + "local newTokenKey = ARGV[5] .. ARGV[2] "
                    + "redis.call('SET', newTokenKey, combined, 'EX', ARGV[3]) "
                    + "redis.call('SET', currentKey, ARGV[2], 'EX', ARGV[3]) "
                    + "return {userId, sessionId, ARGV[2], 'OK'}",
            List.class
    );

    private final StringRedisTemplate stringRedisTemplate;

    // 로그인 시 새 세션(sessionId)과 refresh token을 함께 발급한다. fail-open - Redis
    // 장애 시 로그인 자체를 막지 않기 위해 빈 Optional을 반환한다(access token만
    // 발급되고, 클라이언트는 그게 만료되면 재로그인해야 한다 - 로그인 자체가 막히는
    // 것보다는 낫다).
    public Optional<IssueResult> issue(UUID userId) {
        try {
            String token = generateToken();
            String sessionId = UUID.randomUUID().toString();
            String combined = userId + COMBINED_VALUE_SEPARATOR + sessionId;
            stringRedisTemplate.execute(
                    ISSUE_SCRIPT,
                    List.of(TOKEN_KEY_PREFIX + token, CURRENT_KEY_PREFIX + sessionId),
                    combined, token, String.valueOf(REFRESH_TOKEN_TTL.getSeconds())
            );
            return Optional.of(new IssueResult(token, sessionId));
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
    @SuppressWarnings("unchecked")
    public Optional<RotationResult> rotate(String presentedToken) {
        String newToken = generateToken();
        List<Object> result = stringRedisTemplate.execute(
                ROTATE_SCRIPT,
                List.of(TOKEN_KEY_PREFIX + presentedToken),
                presentedToken, newToken, String.valueOf(REFRESH_TOKEN_TTL.getSeconds()),
                CURRENT_KEY_PREFIX, TOKEN_KEY_PREFIX, COMBINED_VALUE_SEPARATOR
        );

        if (result == null || result.size() < 4) {
            return Optional.empty();
        }

        String userIdValue = (String) result.get(0);
        String sessionId = (String) result.get(1);
        String status = (String) result.get(3);

        if ("UNKNOWN_TOKEN".equals(status)) {
            // 존재하지 않거나 이미 자연 만료된 토큰 - 흔한 케이스라 경고 로그는 남기지 않는다
            return Optional.empty();
        }

        if ("NO_SESSION".equals(status)) {
            // 로그아웃 등으로 그 세션 자체가 이미 없는 상태 - 공격 신호가 아니므로 INFO로만 남긴다
            log.info("세션이 없는 상태에서 refresh 시도됨(로그아웃 이후 등) - userId={}, sessionId={}",
                    userIdValue, sessionId);
            return Optional.empty();
        }

        if ("REUSE_DETECTED".equals(status)) {
            // 이미 회전되어 밀려난 토큰이 다시 제시됨 - 그 세션만 탈취 의심으로 무효화(스크립트 내에서 처리됨).
            // 다른 세션(다른 기기)에는 영향 없다.
            log.warn("Refresh Token 재사용 감지 - userId={}, sessionId={} 해당 세션만 무효화", userIdValue, sessionId);
            return Optional.empty();
        }

        String returnedNewToken = (String) result.get(2);
        return Optional.of(new RotationResult(UUID.fromString(userIdValue), sessionId, returnedNewToken));
    }

    // 로그아웃 시 해당 세션의 refresh token만 무효화한다 - 다른 기기의 세션에는 영향
    // 없다. Redis 장애로 실패해도 로그아웃 자체는 클라이언트 입장에서 성공한 것처럼
    // 처리되는 게 맞다(최악의 경우 기존 토큰이 좀 더 오래 살아있는 것뿐, 보안 우회로
    // 이어지진 않는다).
    public void revoke(String sessionId) {
        try {
            stringRedisTemplate.delete(CURRENT_KEY_PREFIX + sessionId);
        } catch (RuntimeException e) {
            log.warn("Redis 기록 실패로 로그아웃 시 refresh token을 무효화하지 못함", e);
        }
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record IssueResult(String refreshToken, String sessionId) {
    }

    public record RotationResult(UUID userId, String sessionId, String newRefreshToken) {
    }
}
