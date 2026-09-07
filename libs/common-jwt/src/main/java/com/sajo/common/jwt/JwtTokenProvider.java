package com.sajo.common.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 발급/검증 공용 컴포넌트. user-service가 발급, Gateway가 검증하며 둘은 반드시
 * 동일한 sajo.jwt.secret을 써야 한다.
 */
public class JwtTokenProvider {

    private static final int MIN_SECRET_BYTES = 32; // HS256 최소 키 길이(256bit)

    private final SecretKey secretKey;
    private final long accessTokenValiditySeconds;

    public JwtTokenProvider(String secret, long accessTokenValiditySeconds) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("sajo.jwt.secret은 최소 " + MIN_SECRET_BYTES + "바이트 이상이어야 합니다");
        }
        if (accessTokenValiditySeconds <= 0) {
            throw new IllegalStateException("sajo.jwt.access-token-validity-seconds는 0보다 커야 합니다");
        }

        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenValiditySeconds = accessTokenValiditySeconds;
    }

    // sessionId 없이 발급하는 기존 호출부(테스트 등)와의 호환을 위해 유지 - sessionId는
    // null로 발급된다(다중 세션/기기별 로그아웃을 지목할 수 없는 토큰이 된다)
    public String createAccessToken(UUID userId, String role) {
        return createAccessToken(userId, role, null);
    }

    // sessionId - 다중 기기 로그인 지원을 위해 로그인/재발급 시점마다 부여되는 세션 식별자.
    // Gateway가 이 값을 X-Session-Id로 downstream에 전달하고, user-service는 로그아웃 시
    // 이 값으로 "그 기기의 세션만" 정확히 지목해 무효화한다(다른 기기 세션에는 영향 없음).
    public String createAccessToken(UUID userId, String role, String sessionId) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(accessTokenValiditySeconds);

        return Jwts.builder()
                .subject(userId.toString())
                .claim("role", role)
                .claim("sessionId", sessionId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(secretKey)
                .compact();
    }

    public long getAccessTokenValiditySeconds() {
        return accessTokenValiditySeconds;
    }

    // 서명 불일치/만료/형식 오류 모두 JwtValidationException 하나로 통일
    public JwtClaims validateAndGetClaims(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            UUID userId = UUID.fromString(claims.getSubject());
            // role/sessionId 클레임이 없으면 null - 이 클레임들 도입 이전에 발급된 토큰까지
            // 검증 실패로 처리하지 않기 위함 (호출하는 쪽에서 null을 각각 알맞게 처리)
            String role = claims.get("role", String.class);
            String sessionId = claims.get("sessionId", String.class);
            return new JwtClaims(userId, role, sessionId);
        } catch (ExpiredJwtException e) {
            throw new JwtValidationException("만료된 토큰입니다", e);
        } catch (JwtException | IllegalArgumentException e) {
            throw new JwtValidationException("유효하지 않은 토큰입니다", e);
        }
    }
}
