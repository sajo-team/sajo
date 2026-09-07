package com.sajo.trading_service.trading.event;

import java.util.UUID;

public record OrderRequestedEvent(
        UUID orderId
) {
}