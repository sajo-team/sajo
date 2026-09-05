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
// 저장 스키마:
//   refresh-token:{token}    -> userId  (TTL 14일, 발급 시점 기준)
//   refresh-current:{userId} -> 현재 유효한 token 값 (TTL 14일, 회전마다 갱신)
//
// 회전(rotation) + 재사용 탐지: refresh 요청마다 새 토큰을 발급하고, 쓰인 토큰은
// "현재 유효한 토큰"에서 밀려난다. 이미 회전되어 밀려난(=한 번 쓰인) 토큰이 다시
// 제시되면 탈취로 간주해 해당 사용자의 세션을 통째로 무효화한다 - 그 순간 요청을
// 보낸 쪽이 진짜 사용자인지 탈취범인지 서버가 구분할 수 없으므로, 이미 재발급된
// "현재" 토큰까지 같이 죽여야 탈취범이 들고 있는 새 토큰도 무효화된다.
//
// 리뷰 반영 - issue()/rotate() 둘 다 Lua 스크립트로 원자화한다: "현재 토큰과 비교
// 후 교체" 같은 검증→갱신을 별도의 Redis 호출로 나눠서 하면(check-then-act),
// 같은 토큰으로 거의 동시에 두 요청이 들어올 때(네트워크 재시도, 다중 탭 등) 둘 다
// 검증을 통과한 뒤 서로의 갱신을 덮어써서, 먼저 응답받은 클라이언트의 새 토큰이
// 곧바로 "밀려난" 상태가 되어 다음 refresh에서 재사용(탈취)으로 오탐될 수 있다.
// EVAL로 실행되는 Lua 스크립트는 그 안의 모든 명령을 하나의 원자적 연산으로
// 실행하므로 이 틈이 생기지 않는다.
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final String TOKEN_KEY_PREFIX = "user-service:refresh-token:";
    private static final String CURRENT_KEY_PREFIX = "user-service:refresh-current:";
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(14);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // KEYS[1] = TOKEN_KEY_PREFIX + newToken, KEYS[2] = CURRENT_KEY_PREFIX + userId
    // ARGV[1] = userId, ARGV[2] = newToken, ARGV[3] = ttlSeconds
    private static final RedisScript<String> ISSUE_SCRIPT = RedisScript.of(
            "redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[3]) "
                    + "redis.call('SET', KEYS[2], ARGV[2], 'EX', ARGV[3]) "
                    + "return 'OK'",
            String.class
    );

    // KEYS[1] = TOKEN_KEY_PREFIX + presentedToken
    // ARGV[1] = presentedToken, ARGV[2] = newToken, ARGV[3] = ttlSeconds,
    // ARGV[4] = CURRENT_KEY_PREFIX, ARGV[5] = TOKEN_KEY_PREFIX
    //
    // 반환값은 항상 3개짜리 배열 {userId, newTokenOrEmpty, status} - Lua에서 nil을
    // 반환하는 경로를 아예 두지 않는다. 실제로 겪은 문제(리뷰 반영): Lua가 nil을
    // 반환하면 Spring Data Redis(RedisScript<List>)가 이를 Java null이나 빈 List로
    // 일관되지 않게 변환하면서, 스크립트 실행 자체에서 IndexOutOfBoundsException이
    // 발생했다. 모든 경로에서 형태가 동일한 배열을 반환하도록 해서 이 변환 문제 자체를
    // 피한다.
    //   status = "UNKNOWN_TOKEN"   : 존재하지 않거나 이미 자연 만료된 토큰
    //   status = "NO_SESSION"      : 세션이 없음(로그아웃되었거나 이미 무효화된 상태) - 공격 아님
    //   status = "REUSE_DETECTED"  : 이미 회전되어 밀려난 토큰이 다시 제시됨 - 탈취 의심
    //   status = "OK"              : 정상 회전 성공
    private static final RedisScript<List> ROTATE_SCRIPT = RedisScript.of(
            "local userId = redis.call('GET', KEYS[1]) "
                    + "if not userId then return {'', '', 'UNKNOWN_TOKEN'} end "
                    + "local currentKey = ARGV[4] .. userId "
                    + "local currentToken = redis.call('GET', currentKey) "
                    + "if not currentToken then "
                    + "  return {userId, '', 'NO_SESSION'} "
                    + "end "
                    + "if currentToken ~= ARGV[1] then "
                    + "  redis.call('DEL', currentKey) "
                    + "  return {userId, '', 'REUSE_DETECTED'} "
                    + "end "
                    + "local newTokenKey = ARGV[5] .. ARGV[2] "
                    + "redis.call('SET', newTokenKey, userId, 'EX', ARGV[3]) "
                    + "redis.call('SET', currentKey, ARGV[2], 'EX', ARGV[3]) "
                    + "return {userId, ARGV[2], 'OK'}",
            List.class
    );

    private final StringRedisTemplate stringRedisTemplate;

    // 로그인 시 새 refresh token을 발급한다. fail-open - Redis 장애 시 로그인 자체를
    // 막지 않기 위해 빈 Optional을 반환한다(access token만 발급되고, 클라이언트는
    // 그게 만료되면 재로그인해야 한다 - 로그인 자체가 막히는 것보다는 낫다).
    public Optional<String> issue(UUID userId) {
        try {
            String token = generateToken();
            String userIdValue = userId.toString();
            stringRedisTemplate.execute(
                    ISSUE_SCRIPT,
                    List.of(TOKEN_KEY_PREFIX + token, CURRENT_KEY_PREFIX + userIdValue),
                    userIdValue, token, String.valueOf(REFRESH_TOKEN_TTL.getSeconds())
            );
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
    @SuppressWarnings("unchecked")
    public Optional<RotationResult> rotate(String presentedToken) {
        String newToken = generateToken();
        List<Object> result = stringRedisTemplate.execute(
                ROTATE_SCRIPT,
                List.of(TOKEN_KEY_PREFIX + presentedToken),
                presentedToken, newToken, String.valueOf(REFRESH_TOKEN_TTL.getSeconds()),
                CURRENT_KEY_PREFIX, TOKEN_KEY_PREFIX
        );

        // 스크립트는 항상 3개짜리 배열을 반환하도록 만들어뒀지만, 혹시 모를 상황(연결
        // 문제 등)에 대비한 방어적 체크
        if (result == null || result.size() < 3) {
            return Optional.empty();
        }

        String userIdValue = (String) result.get(0);
        String status = (String) result.get(2);

        if ("UNKNOWN_TOKEN".equals(status)) {
            // 존재하지 않거나 이미 자연 만료된 토큰 - 흔한 케이스라 경고 로그는 남기지 않는다
            return Optional.empty();
        }

        if ("NO_SESSION".equals(status)) {
            // 로그아웃 등으로 세션 자체가 이미 없는 상태 - 공격 신호가 아니므로 INFO로만 남긴다
            log.info("세션이 없는 상태에서 refresh 시도됨(로그아웃 이후 등) - userId={}", userIdValue);
            return Optional.empty();
        }

        if ("REUSE_DETECTED".equals(status)) {
            // 이미 회전되어 밀려난 토큰이 다시 제시됨 - 탈취 의심, 세션 전체 무효화(스크립트 내에서 처리됨)
            log.warn("Refresh Token 재사용 감지 - userId={} 세션 전체 무효화", userIdValue);
            return Optional.empty();
        }

        String returnedNewToken = (String) result.get(1);
        return Optional.of(new RotationResult(UUID.fromString(userIdValue), returnedNewToken));
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
