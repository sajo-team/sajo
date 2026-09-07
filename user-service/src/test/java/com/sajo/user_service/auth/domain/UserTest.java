package com.sajo.user_service.auth.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("User 도메인 테스트")
class UserTest {

    @Test
    @DisplayName("of()로 생성하면 role이 USER로 채워진다")
    void of_setsRoleToUser() {
        // when
        User user = User.of("test@sajo.com", "encoded-password", "테스트");

        // then
        assertThat(user.getRole()).isEqualTo(Role.USER);
    }

    // 리뷰 반영 - 예전에는 "builder()를 직접 호출해서 role(...)을 빠뜨리면 null이 될 수
    // 있다"는 걸 검증하는 테스트가 있었지만, 지금은 builder()가 private이라 User 클래스
    // 밖(이 테스트 클래스 포함)에서는 애초에 호출 자체가 컴파일되지 않는다. 즉 이 문제는
    // "런타임에 확인하는 테스트"가 아니라 "컴파일 타임에 막히는 것"으로 바뀌었다.
}