package com.sajo.user_service.auth.service.query;
 
import com.sajo.common.exception.BusinessException;
import com.sajo.common.jwt.JwtTokenProvider;
import com.sajo.user_service.auth.controller.dto.request.LoginRequest;
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
 
@ExtendWith(MockitoExtension.class)
class AuthQueryServiceTest {
 
    @Mock
    private UserQueryRepository userQueryRepository;
 
    @Mock
    private PasswordEncoder passwordEncoder;
 
    @Mock
    private JwtTokenProvider jwtTokenProvider;
 
    @Mock
    private LoginAttemptService loginAttemptService;
 
    private AuthQueryService authQueryService;
 
    @BeforeEach
    void setUp() {
        authQueryService = new AuthQueryService(
                userQueryRepository, passwordEncoder, jwtTokenProvider, loginAttemptService);
    }
 
    @Test
    @DisplayName("이메일과 비밀번호가 맞으면 토큰을 발급하고 실패 카운터를 지운다")
    void loginSucceeds() {
        // given
        User user = User.of("test@sajo.com", "encoded-password", "테스트");
        UUID userId = UUID.randomUUID();
        ReflectionTestUtils.setField(user, "id", userId); // id는 원래 DB가 채워주는 값이라 순수 단위테스트에서 직접 주입
        LoginRequest request = new LoginRequest("test@sajo.com", "raw-password");
 
        given(userQueryRepository.findByEmail("test@sajo.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("raw-password", "encoded-password")).willReturn(true);
        given(jwtTokenProvider.createAccessToken(userId, "USER")).willReturn("issued-token");
        given(jwtTokenProvider.getAccessTokenValiditySeconds()).willReturn(3600L);
 
        // when
        LoginResponse response = authQueryService.login(request);
 
        // then
        assertThat(response).isEqualTo(LoginResponse.of("issued-token", 3600L));
        verify(loginAttemptService).recordSuccess("test@sajo.com");
    }
 
    @Test
    @DisplayName("이미 잠긴 이메일이면 자격 확인 없이 즉시 TOO_MANY_LOGIN_ATTEMPTS 예외를 던진다")
    void loginFailsImmediatelyWhenLocked() {
        // given
        LoginRequest request = new LoginRequest("locked@sajo.com", "raw-password");
        given(loginAttemptService.isLocked("locked@sajo.com")).willReturn(true);
 
        // when & then
        assertThatThrownBy(() -> authQueryService.login(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode()).isEqualTo(UserErrorCode.TOO_MANY_LOGIN_ATTEMPTS);
                });
 
        // 잠긴 상태면 자격 확인 자체를 안 해야 한다 (무차별 대입이 DB/BCrypt까지 못 가도록)
        verifyNoInteractions(userQueryRepository, passwordEncoder, jwtTokenProvider);
    }
 
    @Test
    @DisplayName("존재하지 않는 이메일이면 INVALID_CREDENTIALS 예외를 던지고 실패를 기록한다")
    void loginFailsWhenEmailNotFound() {
        // given
        LoginRequest request = new LoginRequest("unknown@sajo.com", "raw-password");
        given(userQueryRepository.findByEmail("unknown@sajo.com")).willReturn(Optional.empty());
 
        // when & then
        assertThatThrownBy(() -> authQueryService.login(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode()).isEqualTo(UserErrorCode.INVALID_CREDENTIALS);
                });
 
        // 이메일이 없어도 BCrypt 비교를 한 번 수행해야 한다 (타이밍 사이드채널 방지 - 리뷰 반영)
        verify(passwordEncoder).matches(eq("raw-password"), anyString());
        verify(loginAttemptService).recordFailure("unknown@sajo.com");
        verify(loginAttemptService, never()).recordSuccess(anyString());
        verifyNoInteractions(jwtTokenProvider);
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
        assertThatThrownBy(() -> authQueryService.login(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode()).isEqualTo(UserErrorCode.INVALID_CREDENTIALS);
                });
 
        verify(loginAttemptService).recordFailure("test@sajo.com");
        verifyNoInteractions(jwtTokenProvider);
    }
}
