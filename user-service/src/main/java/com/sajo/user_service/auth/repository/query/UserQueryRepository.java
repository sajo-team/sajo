package com.sajo.user_service.auth.repository.query;

import com.sajo.user_service.auth.domain.User;
import com.sajo.user_service.auth.domain.UserRepository;

import java.util.Optional;

// 로그인용 조회 Repository (UserCommandRepository와 동일 패턴)
public interface UserQueryRepository extends UserRepository {

    Optional<User> findByEmail(String email);
}
