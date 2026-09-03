package com.sajo.user_service.account.repository.query;

import com.sajo.user_service.account.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AccountQueryRepository extends JpaRepository<Account, UUID> {
    boolean existsByAccountNoHashAndDeletedAtIsNull(String accountNoHash);
    boolean existsByUserIdAndDeletedAtIsNull(UUID userId);

    Optional<Account> findByUserIdAndDeletedAtIsNull(UUID userId);
}
