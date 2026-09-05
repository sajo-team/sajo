package com.sajo.user_service.auth.service.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.common.jwt.JwtTokenProvider;
import com.sajo.user_service.auth.controller.dto.request.LoginRequest;
import com.sajo.user_service.auth.controller.dto.response.LoginResponse;
import com.sajo.user_service.auth.domain.User;
import com.sajo.user_service.auth.exception.UserErrorCode;
import com.sajo.user_service.auth.repository.query.UserQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 로그인은 상태 변경이 없는 조회 작업(자격 확인 + 토큰 발급)이라 Query 계층에 둔다
@Service
@RequiredArgsConstructor
public class AuthQueryService {

    // 이메일이 존재하지 않을 때도 이 해시로 BCrypt 비교를 수행해서, 이메일 존재 여부에 따라
    // 응답 시간이 달라지는 타이밍 사이드채널을 없앤다. 실제 사용자 비밀번호와는 무관한 값이라
    // 어떤 입력으로도 일치하지 않는다 - 오직 "BCrypt 연산을 한 번 더 하기 위한 더미".
    private static final String DUMMY_BCRYPT_HASH =
            "$2a$10$hjbnx74waXs6VBwUehsKKuIsz4TiDQFFGL98e8KhbjM52W4tlTIP2";

    private final UserQueryRepository userQueryRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userQueryRepository.findByEmail(request.email()).orElse(null);
        String hashToCheck = (user != null) ? user.getPassword() : DUMMY_BCRYPT_HASH;
        boolean matches = passwordEncoder.matches(request.password(), hashToCheck);

        if (user == null || !matches) {
            throw new BusinessException(UserErrorCode.INVALID_CREDENTIALS);
        }

        String accessToken = jwtTokenProvider.createAccessToken(user.getId());
        return LoginResponse.of(accessToken, jwtTokenProvider.getAccessTokenValiditySeconds());
    }
}