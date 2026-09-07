package com.sajo.user_service.auth.service.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.user_service.auth.controller.dto.response.UserStatusResponse;
import com.sajo.user_service.auth.domain.User;
import com.sajo.user_service.auth.exception.UserErrorCode;
import com.sajo.user_service.auth.repository.query.UserQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// 다른 서비스가 특정 userId의 존재 여부와 상태(정상/탈퇴)를 확인하기 위한 내부 전용 조회.
// 관리자 기능으로 계정 정지가 필요해지면 그때 별도로 추가한다.
//
// 지금은 존재 여부+상태만 반환하지만, 나중에 다른 서비스가 이름/이메일 등 추가 필드를
// 필요로 하면 이 API의 응답에 필드를 추가하는 방식으로 대응한다(새 API를 만들지 않는다).
@Service
@RequiredArgsConstructor
public class UserInternalQueryService {

    private final UserQueryRepository userQueryRepository;

    // 리뷰 반영 - 이 메서드는 Redis 등 외부 I/O 없이 순수하게 DB 조회 하나만 수행하므로,
    // AuthCommandService.login()/refresh()와 달리 @Transactional(readOnly = true)를
    // 붙여도 트랜잭션 범위 안에 외부 호출이 들어가는 문제가 없다. 같은 서비스의
    // AccountQueryService 컨벤션과도 일관되고, 나중에 이 메서드에 추가 조회가 붙을
    // 경우를 대비해 트랜잭션 경계를 명시적으로 둔다.
    @Transactional(readOnly = true)
    public UserStatusResponse getUserStatus(UUID userId) {
        User user = userQueryRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
        return UserStatusResponse.of(user.getId(), user.isDeleted());
    }
}
