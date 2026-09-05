package com.sajo.user_service.auth.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.common.jwt.JwtTokenProvider;
import com.sajo.user_service.auth.controller.dto.request.LoginRequest;
import com.sajo.user_service.auth.controller.dto.request.RefreshRequest;
import com.sajo.user_service.auth.controller.dto.response.LoginResponse;
import com.sajo.user_service.auth.domain.User;
import com.sajo.user_service.auth.exception.UserErrorCode;
import com.sajo.user_service.auth.repository.query.UserQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

// 리뷰 반영 - login()/refresh()/logout() 전부 Redis 상태(로그인 실패 카운터, refresh
// token 세션)를 실제로 변경하는 작업이라 Command로 통합했다 (예전에는 login/refresh가
// "팀 컨벤션상 Query로 취급한다"는 주석과 함께 Query 계층에 있었으나, 그 컨벤션이
// 실제로 합의된 적이 없어 CLAUDE.md 10절에 따라 Command로 옮김). LoginAttemptService/
// RefreshTokenService도 같은 이유로 이제 command 패키지에 있다.
@ExtendWith(MockitoExtension.class)
class AuthCommandServiceTest {

    @Mock
    private UserQueryRepository userQueryRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private LoginAttemptService loginAttemptService;

    @Mock
    private RefreshTokenService refreshTokenService;

    private AuthCommandService authCommandService;

    @BeforeEach
    void setUp() {
        authCommandService = new AuthCommandService(
                userQueryRepository, passwordEncoder, jwtTokenProvider, loginAttemptService, refreshTokenService);
    }

    @Test
    @DisplayName("이메일과 비밀번호가 맞으면 access/refresh token을 발급하고 실패 카운터를 지운다")
    void loginSucceeds() {
        // given
        User user = User.of("test@sajo.com", "encoded-password", "테스트");
        UUID userId = UUID.randomUUID();
        ReflectionTestUtils.setField(user, "id", userId); // id는 원래 DB가 채워주는 값이라 순수 단위테스트에서 직접 주입
        LoginRequest request = new LoginRequest("test@sajo.com", "raw-password");

        given(userQueryRepository.findByEmail("test@sajo.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("raw-password", "encoded-password")).willReturn(true);
        given(refreshTokenService.issue(userId))
                .willReturn(Optional.of(new RefreshTokenService.IssueResult("issued-refresh-token", "session-1")));
        given(jwtTokenProvider.createAccessToken(userId, "USER", "session-1")).willReturn("issued-access-token");
        given(jwtTokenProvider.getAccessTokenValiditySeconds()).willReturn(3600L);

        // when
        LoginResponse response = authCommandService.login(request);

        // then
        assertThat(response).isEqualTo(LoginResponse.of("issued-access-token", "issued-refresh-token", 3600L));
        verify(loginAttemptService).recordSuccess("test@sajo.com");
    }

    @Test
    @DisplayName("refresh token 발급이 실패해도(Redis 장애) 로그인 자체는 access token으로 성공한다")
    void loginSucceedsWithNullRefreshTokenWhenIssueFails() {
        // given - RefreshTokenService의 fail-open 정책을 재현 (빈 Optional 반환)
        User user = User.of("test@sajo.com", "encoded-password", "테스트");
        UUID userId = UUID.randomUUID();
        ReflectionTestUtils.setField(user, "id", userId);
        LoginRequest request = new LoginRequest("test@sajo.com", "raw-password");

        given(userQueryRepository.findByEmail("test@sajo.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("raw-password", "encoded-password")).willReturn(true);
        given(refreshTokenService.issue(userId)).willReturn(Optional.empty());
        given(jwtTokenProvider.createAccessToken(userId, "USER", null)).willReturn("issued-access-token");
        given(jwtTokenProvider.getAccessTokenValiditySeconds()).willReturn(3600L);

        // when
        LoginResponse response = authCommandService.login(request);

        // then
        assertThat(response.accessToken()).isEqualTo("issued-access-token");
        assertThat(response.refreshToken()).isNull();
    }

    @Test
    @DisplayName("이미 잠긴 이메일이면 자격 확인 없이 즉시 TOO_MANY_LOGIN_ATTEMPTS 예외를 던진다")
    void loginFailsImmediatelyWhenLocked() {
        // given
        LoginRequest request = new LoginRequest("locked@sajo.com", "raw-password");
        given(loginAttemptService.isLocked("locked@sajo.com")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> authCommandService.login(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode()).isEqualTo(UserErrorCode.TOO_MANY_LOGIN_ATTEMPTS);
                });

        // 잠긴 상태면 자격 확인 자체를 안 해야 한다 (무차별 대입이 DB/BCrypt까지 못 가도록)
        verifyNoInteractions(userQueryRepository, passwordEncoder, jwtTokenProvider, refreshTokenService);
    }

    @Test
    @DisplayName("존재하지 않는 이메일이면 INVALID_CREDENTIALS 예외를 던지고 실패를 기록한다")
    void loginFailsWhenEmailNotFound() {
        // given
        LoginRequest request = new LoginRequest("unknown@sajo.com", "raw-password");
        given(userQueryRepository.findByEmail("unknown@sajo.com")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> authCommandService.login(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode()).isEqualTo(UserErrorCode.INVALID_CREDENTIALS);
                });

        // 이메일이 없어도 BCrypt 비교를 한 번 수행해야 한다 (타이밍 사이드채널 방지 - 리뷰 반영)
        verify(passwordEncoder).matches(eq("raw-password"), anyString());
        verify(loginAttemptService).recordFailure("unknown@sajo.com");
        verify(loginAttemptService, never()).recordSuccess(anyString());
        verifyNoInteractions(jwtTokenProvider, refreshTokenService);
    }

    @Test
    @DisplayName("비밀번호가 틀리면 INVALID_CREDENTIALS 예외를 던지고 실패를 기록한다")
    void loginFailsWhenPasswordDoesNotMatch() {
        // given
        User user = User.of("test@sajo.com", "encoded-password", "테스트");
        LoginRequest request = new LoginRequest("test@sajo.com", "wrong-password");

        given(userQueryRepository.findByEmail("test@sajo.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrong-password", "encoded-password")).willReturn(false);

        // when & then
        assertThatThrownBy(() -> authCommandService.login(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode()).isEqualTo(UserErrorCode.INVALID_CREDENTIALS);
                });

        verify(loginAttemptService).recordFailure("test@sajo.com");
        verifyNoInteractions(jwtTokenProvider, refreshTokenService);
    }

    @Test
    @DisplayName("유효한 refresh token이면 새 access/refresh token을 발급하고 같은 sessionId를 유지한다")
    void refreshSucceeds() {
        // given
        User user = User.of("test@sajo.com", "encoded-password", "테스트");
        UUID userId = UUID.randomUUID();
        ReflectionTestUtils.setField(user, "id", userId);
        RefreshRequest request = new RefreshRequest("old-refresh-token");

        given(refreshTokenService.peekUserId("old-refresh-token")).willReturn(Optional.of(userId));
        given(userQueryRepository.findById(userId)).willReturn(Optional.of(user));
        given(refreshTokenService.rotate("old-refresh-token"))
                .willReturn(Optional.of(new RefreshTokenService.RotationResult(userId, "session-1", "new-refresh-token")));
        given(jwtTokenProvider.createAccessToken(userId, "USER", "session-1")).willReturn("new-access-token");
        given(jwtTokenProvider.getAccessTokenValiditySeconds()).willReturn(3600L);

        // when
        LoginResponse response = authCommandService.refresh(request);

        // then
        assertThat(response).isEqualTo(LoginResponse.of("new-access-token", "new-refresh-token", 3600L));
    }

    @Test
    @DisplayName("토큰 자체를 모르면(peekUserId 실패) INVALID_REFRESH_TOKEN 예외를 던지고 DB/회전을 시도하지 않는다")
    void refreshFailsWhenTokenCompletelyUnknown() {
        // given
        RefreshRequest request = new RefreshRequest("unknown-token");
        given(refreshTokenService.peekUserId("unknown-token")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> authCommandService.refresh(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode()).isEqualTo(UserErrorCode.INVALID_REFRESH_TOKEN);
                });

        verifyNoInteractions(userQueryRepository, jwtTokenProvider);
        verify(refreshTokenService, never()).rotate(anyString());
    }

    // 리뷰 반영 - 순서 문제(High)를 직접 검증하는 핵심 테스트: DB 조회가 실패(사용자가
    // 존재하지 않음)하면 실제 회전(rotate)이 전혀 호출되지 않아야 한다. 예전 순서(rotate
    // 먼저 → findById 나중)였다면 이 시점에 이미 Redis에서 토큰이 회전되어버린 뒤였다 -
    // 즉 DB 실패로 예외가 나가는 순간 옛 토큰은 이미 무효화됐지만 새 토큰은 클라이언트에게
    // 전달되지 못해 그 세션이 복구 불가능한 상태가 됐다. 지금 순서(DB 확인 → 회전)라면
    // DB 실패 시 회전 자체가 아예 일어나지 않아 옛 토큰이 그대로 유효하게 남는다.
    @Test
    @DisplayName("회전 전에 사용자가 존재하는지 먼저 확인하고, 없으면 회전을 아예 시도하지 않는다")
    void refreshFailsWhenUserNoLongerExistsAndNeverRotates() {
        // given
        UUID userId = UUID.randomUUID();
        RefreshRequest request = new RefreshRequest("old-refresh-token");
        given(refreshTokenService.peekUserId("old-refresh-token")).willReturn(Optional.of(userId));
        given(userQueryRepository.findById(userId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> authCommandService.refresh(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode()).isEqualTo(UserErrorCode.INVALID_REFRESH_TOKEN);
                });

        // 핵심 검증 - 회전 자체가 호출되지 않아야 옛 토큰이 그대로 살아있다
        verify(refreshTokenService, never()).rotate(anyString());
        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    @DisplayName("DB 확인 이후 회전 시점에 재사용이 감지되면 INVALID_REFRESH_TOKEN 예외를 던진다")
    void refreshFailsWhenRotateDetectsReuseAfterDbCheck() {
        // given - peekUserId는 성공했지만(토큰 자체는 한때 유효했음), 그 사이 다른 요청이
        // 먼저 회전을 완료해 실제 rotate() 시점에는 재사용으로 감지되는 경합 상황을 재현
        UUID userId = UUID.randomUUID();
        User user = User.of("test@sajo.com", "encoded-password", "테스트");
        ReflectionTestUtils.setField(user, "id", userId);
        RefreshRequest request = new RefreshRequest("old-refresh-token");

        given(refreshTokenService.peekUserId("old-refresh-token")).willReturn(Optional.of(userId));
        given(userQueryRepository.findById(userId)).willReturn(Optional.of(user));
        given(refreshTokenService.rotate("old-refresh-token")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> authCommandService.refresh(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode()).isEqualTo(UserErrorCode.INVALID_REFRESH_TOKEN);
                });

        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    @DisplayName("로그아웃하면 해당 세션의 refresh token만 무효화한다 (다른 기기 세션에는 영향 없음)")
    void logoutRevokesOnlyThatSession() {
        // given
        String sessionId = "session-abc";

        // when
        authCommandService.logout(sessionId);

        // then
        verify(refreshTokenService).revoke(sessionId);
    }

    // 리뷰 반영 - 로그인 시점에 Redis 장애로 fail-open되어 sessionId 없이 access token이
    // 발급된 경우, 그 세션은 revoke할 대상 자체가 없다. 이런 경우 로그아웃 요청이 실패로
    // 처리되지 않고(예외 없이) 조용히 아무것도 안 하고 끝나야 한다 - revoke()의 기존
    // "로그아웃은 클라이언트 입장에서 성공한 것처럼 처리되는 게 맞다"는 철학과 일관되게.
    @Test
    @DisplayName("sessionId가 null이면 아무것도 하지 않고 조용히 끝난다 (fail-open 로그인 이후 로그아웃 대응)")
    void logoutWithNullSessionIdIsNoOp() {
        // when
        authCommandService.logout(null);

        // then
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    @DisplayName("sessionId가 빈 문자열이어도 아무것도 하지 않고 조용히 끝난다")
    void logoutWithBlankSessionIdIsNoOp() {
        // when
        authCommandService.logout("   ");

        // then
        verifyNoInteractions(refreshTokenService);
    }
}
