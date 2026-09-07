package com.sajo.user_service.auth.service.query;

import com.sajo.common.exception.BusinessException;
import com.sajo.user_service.auth.controller.dto.response.UserStatusResponse;
import com.sajo.user_service.auth.domain.User;
import com.sajo.user_service.auth.exception.UserErrorCode;
import com.sajo.user_service.auth.repository.query.UserQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

// 다른 서비스가 특정 userId의 존재 여부와 상태(정상/탈퇴)를 확인하기 위한 내부 전용 조회.
// "정지" 상태는 현재 User 도메인에 그 개념 자체가 없어 이번 범위에서 제외한다 - 나중에
// 관리자 기능으로 계정 정지가 필요해지면 그때 별도로 추가한다.
//
// 지금은 존재 여부+상태만 반환하지만, 나중에 다른 서비스가 이름/이메일 등 추가 필드를
// 필요로 하면 이 API의 응답에 필드를 추가하는 방식으로 대응한다(새 API를 만들지 않는다).
@Service
@RequiredArgsConstructor
public class UserInternalQueryService {

    private final UserQueryRepository userQueryRepository;

    public UserStatusResponse getUserStatus(UUID userId) {
        User user = userQueryRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
        return UserStatusResponse.of(user.getId(), user.isDeleted());
    }
}
