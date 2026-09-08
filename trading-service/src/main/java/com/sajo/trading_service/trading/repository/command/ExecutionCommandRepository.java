package com.sajo.trading_service.trading.repository.command;

import com.sajo.trading_service.trading.domain.Execution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ExecutionCommandRepository
        extends JpaRepository<Execution, UUID> {

    Optional<Execution> findByOrderId(UUID orderId);
}