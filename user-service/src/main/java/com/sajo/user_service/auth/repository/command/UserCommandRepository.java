package com.sajo.user_service.auth.repository.command;

import com.sajo.user_service.auth.domain.UserRepository;

/**
 * 회원가입 등 쓰기 작업에서 사용하는 Repository.
 * 지금은 UserRepository(JPA 기본 CRUD)를 그대로 상속받아 쓰고 있고,
 * command 전용 커스텀 쿼리가 필요해지면 여기에 추가하면 됩니다.
 */
public interface UserCommandRepository extends UserRepository {
}
