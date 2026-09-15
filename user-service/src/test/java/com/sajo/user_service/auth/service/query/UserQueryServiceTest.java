package com.sajo.user_service.auth.service.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.user_service.auth.domain.Role;
import com.sajo.user_service.auth.domain.User;
import com.sajo.user_service.auth.exception.UserErrorCode;
import com.sajo.user_service.auth.repository.query.UserQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class UserQueryServiceTest {

    @Mock
    private UserQueryRepository userQueryRepository;

    private UserQueryService userQueryService;

    @BeforeEach
    void setUp() {
        userQueryService = new UserQueryService(userQueryRepository);
    }

    @Test
    @DisplayName("존재하는 userId로 조회하면 사용자 정보를 반환한다")
    void getMyInfoReturnsUser() {
        // given
        UUID userId = UUID.randomUUID();
        User user = User.of("test@sajo.com", "encoded-password", "테스트");
        ReflectionTestUtils.setField(user, "id", userId);
        given(userQueryRepository.findById(userId)).willReturn(Optional.of(user));

        // when
        User result = userQueryService.getMyInfo(userId);

        // then
        assertThat(result.getId()).isEqualTo(userId);
        assertThat(result.getEmail()).isEqualTo("test@sajo.com");
        assertThat(result.getName()).isEqualTo("테스트");
        assertThat(result.getRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("존재하지 않는 userId로 조회하면 USER_NOT_FOUND 예외를 던진다")
    void getMyInfoThrowsWhenUserNotFound() {
        // given
        UUID userId = UUID.randomUUID();
        given(userQueryRepository.findById(userId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userQueryService.getMyInfo(userId))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND);
                });
    }
}
