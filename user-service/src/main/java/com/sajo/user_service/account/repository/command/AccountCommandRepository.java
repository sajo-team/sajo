package com.sajo.user_service.account.repository.command;

import com.sajo.user_service.account.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AccountCommandRepository extends JpaRepository<Account, UUID> {
    boolean existsByAccountNoHash(String accountNoHash);
    boolean existsByUserId(UUID userId);
}
