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

    public String createAccessToken(UUID userId) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(accessTokenValiditySeconds);

        return Jwts.builder()
                .subject(userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(secretKey)
                .compact();
    }

    public long getAccessTokenValiditySeconds() {
        return accessTokenValiditySeconds;
    }

    // 서명 불일치/만료/형식 오류 모두 JwtValidationException 하나로 통일
    public UUID validateAndGetUserId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return UUID.fromString(claims.getSubject());
        } catch (ExpiredJwtException e) {
            throw new JwtValidationException("만료된 토큰입니다", e);
        } catch (JwtException | IllegalArgumentException e) {
            throw new JwtValidationException("유효하지 않은 토큰입니다", e);
        }
    }
}
