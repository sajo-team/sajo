package com.sajo.user_service.account.repository.command;

import com.sajo.user_service.account.domain.KisTokenLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface KisTokenLogCommandRepository extends JpaRepository<KisTokenLog, UUID> {
}
