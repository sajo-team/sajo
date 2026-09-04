package com.sajo.common.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwtTokenProvider 테스트")
class JwtTokenProviderTest {

    private static final String SECRET = "unit-test-jwt-secret-value-must-be-at-least-32-bytes-long";

    private final JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(SECRET, 3600);

    @Test
    @DisplayName("발급한 토큰을 검증하면 동일한 userId를 반환한다")
    void createAndValidateRoundTrip() {
        // given
        UUID userId = UUID.randomUUID();

        // when
        String token = jwtTokenProvider.createAccessToken(userId);
        UUID result = jwtTokenProvider.validateAndGetUserId(token);

        // then
        assertThat(result).isEqualTo(userId);
    }

    @Test
    @DisplayName("만료된 토큰은 검증에 실패한다")
    void expiredTokenFails() {
        // given - 이미 만료 시각이 지난 토큰을 직접 만든다
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Instant expiredAt = Instant.now().minusSeconds(10);
        String expiredToken = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuedAt(Date.from(expiredAt.minusSeconds(3600)))
                .expiration(Date.from(expiredAt))
                .signWith(key)
                .compact();

        // when & then
        assertThatThrownBy(() -> jwtTokenProvider.validateAndGetUserId(expiredToken))
                .isInstanceOf(JwtValidationException.class);
    }

    @Test
    @DisplayName("다른 키로 서명된(위조된) 토큰은 검증에 실패한다")
    void tamperedSignatureFails() {
        // given
        String differentSecret = "a-completely-different-secret-value-that-is-also-32-bytes-plus";
        SecretKey otherKey = Keys.hmacShaKeyFor(differentSecret.getBytes(StandardCharsets.UTF_8));
        String tokenSignedWithOtherKey = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(otherKey)
                .compact();

        // when & then
        assertThatThrownBy(() -> jwtTokenProvider.validateAndGetUserId(tokenSignedWithOtherKey))
                .isInstanceOf(JwtValidationException.class);
    }

    @Test
    @DisplayName("형식이 완전히 잘못된 문자열은 검증에 실패한다")
    void malformedTokenFails() {
        assertThatThrownBy(() -> jwtTokenProvider.validateAndGetUserId("not-a-jwt-at-all"))
                .isInstanceOf(JwtValidationException.class);
    }

    @Test
    @DisplayName("서명은 유효하지만 subject가 UUID 형식이 아니면 검증에 실패한다")
    void validSignatureButNonUuidSubjectFails() {
        // given - 파싱/서명 검증은 통과하지만 subject가 UUID.fromString에서 실패하는 토큰
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String tokenWithNonUuidSubject = Jwts.builder()
                .subject("not-a-uuid")
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(key)
                .compact();

        // when & then
        assertThatThrownBy(() -> jwtTokenProvider.validateAndGetUserId(tokenWithNonUuidSubject))
                .isInstanceOf(JwtValidationException.class);
    }

    @Test
    @DisplayName("secret이 32바이트 미만이면 생성 시점에 실패한다")
    void shortSecretRejectedAtConstruction() {
        assertThatThrownBy(() -> new JwtTokenProvider("too-short-secret", 3600))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("만료 시간이 0 이하면 생성 시점에 실패한다")
    void nonPositiveValiditySecondsRejectedAtConstruction() {
        assertThatThrownBy(() -> new JwtTokenProvider(SECRET, 0))
                .isInstanceOf(IllegalStateException.class);
    }
}