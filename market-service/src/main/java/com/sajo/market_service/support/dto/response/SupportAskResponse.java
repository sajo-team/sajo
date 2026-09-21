package com.sajo.market_service.support.dto.response;

import java.util.List;

public record SupportAskResponse(
        String answer,
        List<SupportSourceReference> sources
) {
}
