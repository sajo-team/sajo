package com.sajo.user_service.auth.service.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.user_service.auth.controller.dto.response.UserStatusResponse;
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
class UserInternalQueryServiceTest {

    @Mock
    private UserQueryRepository userQueryRepository;

    private UserInternalQueryService userInternalQueryService;

    @BeforeEach
    void setUp() {
        userInternalQueryService = new UserInternalQueryService(userQueryRepository);
    }

    @Test
    @DisplayName("정상 사용자를 조회하면 ACTIVE 상태를 반환한다")
    void getUserStatusReturnsActiveForNormalUser() {
        // given
        UUID userId = UUID.randomUUID();
        User user = User.of("test@sajo.com", "encoded-password", "테스트");
        ReflectionTestUtils.setField(user, "id", userId);
        given(userQueryRepository.findById(userId)).willReturn(Optional.of(user));

        // when
        UserStatusResponse response = userInternalQueryService.getUserStatus(userId);

        // then
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.status()).isEqualTo(UserStatusResponse.UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("탈퇴(soft-delete)한 사용자를 조회하면 WITHDRAWN 상태를 반환한다")
    void getUserStatusReturnsWithdrawnForDeletedUser() {
        // given
        UUID userId = UUID.randomUUID();
        User user = User.of("test@sajo.com", "encoded-password", "테스트");
        ReflectionTestUtils.setField(user, "id", userId);
        user.softDelete(userId); // 본인 탈퇴 상황을 재현

        given(userQueryRepository.findById(userId)).willReturn(Optional.of(user));

        // when
        UserStatusResponse response = userInternalQueryService.getUserStatus(userId);

        // then
        assertThat(response.status()).isEqualTo(UserStatusResponse.UserStatus.WITHDRAWN);
    }

    @Test
    @DisplayName("존재하지 않는 userId를 조회하면 USER_NOT_FOUND 예외를 던진다")
    void getUserStatusThrowsWhenUserNotFound() {
        // given
        UUID userId = UUID.randomUUID();
        given(userQueryRepository.findById(userId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userInternalQueryService.getUserStatus(userId))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND);
                });
    }
}
