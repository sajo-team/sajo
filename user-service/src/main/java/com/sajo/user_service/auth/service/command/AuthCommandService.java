package com.sajo.user_service.auth.service.command;

import com.sajo.common.exception.BusinessException;
import com.sajo.common.jwt.JwtTokenProvider;
import com.sajo.user_service.auth.controller.dto.request.LoginRequest;
import com.sajo.user_service.auth.controller.dto.request.RefreshRequest;
import com.sajo.user_service.auth.controller.dto.response.LoginResponse;
import com.sajo.user_service.auth.domain.User;
import com.sajo.user_service.auth.exception.UserErrorCode;
import com.sajo.user_service.auth.repository.query.UserQueryRepository;
import com.sajo.user_service.auth.service.query.LoginAttemptService;
import com.sajo.user_service.auth.service.query.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

// 로그인/재발급/로그아웃 - 리뷰 반영: login()/refresh()는 자격 확인 자체는 조회성 작업이지만
// 실제로는 Redis 상태(로그인 실패 카운터, refresh token 세션)를 변경한다. 이전에는 이를
// "팀 컨벤션상 Query로 취급한다"는 주석과 함께 Query 계층에 두었으나, 그 컨벤션이 실제로
// 팀 내에서 합의된 적이 없어(CLAUDE.md 10절 - 합의되지 않은 규칙을 새 팀 규칙으로 가정하지
// 않는다) Command로 옮긴다. 같은 자원(refresh token 세션)을 다루는 logout()과도 이제
// 위치가 일관된다.
@Service
@RequiredArgsConstructor
public class AuthCommandService {

    // 이메일이 존재하지 않을 때도 이 해시로 BCrypt 비교를 수행해서, 이메일 존재 여부에 따라
    // 응답 시간이 달라지는 타이밍 사이드채널을 없앤다. 실제 사용자 비밀번호와는 무관한 값이라
    // 어떤 입력으로도 일치하지 않는다 - 오직 "BCrypt 연산을 한 번 더 하기 위한 더미".
    private static final String DUMMY_BCRYPT_HASH =
            "$2a$10$hjbnx74waXs6VBwUehsKKuIsz4TiDQFFGL98e8KhbjM52W4tlTIP2";

    private final UserQueryRepository userQueryRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final LoginAttemptService loginAttemptService;
    private final RefreshTokenService refreshTokenService;

    // 메서드 레벨 @Transactional을 의도적으로 두지 않는다 - 이 메서드가 하는 DB 접근은
    // userQueryRepository.findByEmail() 한 줄뿐이고, UserQueryRepository가 상속하는
    // JpaRepository가 그 호출 자체를 이미 자기 트랜잭션으로 처리한다. 여기에 메서드
    // 전체를 감싸는 트랜잭션을 씌우면 loginAttemptService/refreshTokenService의
    // Redis I/O(외부 호출)까지 그 트랜잭션 범위 안에 들어가 버려서, Redis가 느려질 때
    // DB 커넥션을 불필요하게 붙잡고 있게 된다(CLAUDE.md 7절 - 외부 호출은 트랜잭션
    // 경계 밖에 둔다).
    public LoginResponse login(LoginRequest request) {
        // 이메일 존재 여부와 무관하게 이메일 자체를 키로 잠금 여부를 먼저 확인한다
        // (자격 확인보다 앞서 체크해야 무차별 대입 자체가 자격 확인 로직까지 안 감)
        if (loginAttemptService.isLocked(request.email())) {
            throw new BusinessException(UserErrorCode.TOO_MANY_LOGIN_ATTEMPTS);
        }

        User user = userQueryRepository.findByEmail(request.email()).orElse(null);
        String hashToCheck = (user != null) ? user.getPassword() : DUMMY_BCRYPT_HASH;
        boolean matches = passwordEncoder.matches(request.password(), hashToCheck);

        if (user == null || !matches) {
            loginAttemptService.recordFailure(request.email());
            throw new BusinessException(UserErrorCode.INVALID_CREDENTIALS);
        }

        loginAttemptService.recordSuccess(request.email());

        // 다중 기기 로그인 지원 - 이번 로그인에 새 세션(sessionId)을 부여하고, access
        // token에도 실어서 이후 로그아웃 시 "이 기기의 세션만" 지목할 수 있게 한다.
        Optional<RefreshTokenService.IssueResult> issueResult = refreshTokenService.issue(user.getId());
        String sessionId = issueResult.map(RefreshTokenService.IssueResult::sessionId).orElse(null);
        String refreshToken = issueResult.map(RefreshTokenService.IssueResult::refreshToken).orElse(null);

        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getRole().name(), sessionId);
        return LoginResponse.of(accessToken, refreshToken, jwtTokenProvider.getAccessTokenValiditySeconds());
    }

    // Refresh Token으로 새 Access Token(+ 회전된 새 Refresh Token)을 발급한다.
    // 회전 시점에 사용자의 role을 다시 조회해서 access token에 반영한다 - 로그인 이후
    // role이 바뀐 경우, 기존 access token 자연 만료를 기다리지 않고 다음 refresh 때
    // 곧바로 반영되도록 하기 위함이다. 회전된 세션(sessionId)도 그대로 새 access token에
    // 실어서, 이후 로그아웃이 계속 같은 세션을 정확히 지목할 수 있게 한다.
    public LoginResponse refresh(RefreshRequest request) {
        RefreshTokenService.RotationResult rotationResult = refreshTokenService.rotate(request.refreshToken())
                .orElseThrow(() -> new BusinessException(UserErrorCode.INVALID_REFRESH_TOKEN));

        User user = userQueryRepository.findById(rotationResult.userId())
                .orElseThrow(() -> new BusinessException(UserErrorCode.INVALID_REFRESH_TOKEN));

        String accessToken = jwtTokenProvider.createAccessToken(
                user.getId(), user.getRole().name(), rotationResult.sessionId());
        return LoginResponse.of(
                accessToken, rotationResult.newRefreshToken(), jwtTokenProvider.getAccessTokenValiditySeconds());
    }

    // sessionId 기준으로 로그아웃한다 - 다중 기기 로그인 지원: 이 기기(세션)의 refresh
    // token만 무효화되고, 같은 계정으로 로그인된 다른 기기의 세션에는 영향이 없다.
    //
    // sessionId가 없는 경우(null/blank) - 리뷰 반영: 로그인 시점에 Redis 장애로 fail-open
    // 되어(RefreshTokenService.issue() 참고) sessionId 없이 access token이 발급된 세션이
    // 이 경우에 해당한다. 이런 세션은 애초에 revoke할 대상 자체가 없으므로, "로그아웃 요청은
    // 실패해도 클라이언트 입장에서 성공한 것처럼 처리되는 게 맞다"는 revoke()의 기존
    // 설계 철학과 일관되게 그냥 아무것도 하지 않고 성공 처리한다(400을 반환하지 않는다).
    public void logout(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        refreshTokenService.revoke(sessionId);
    }
}
