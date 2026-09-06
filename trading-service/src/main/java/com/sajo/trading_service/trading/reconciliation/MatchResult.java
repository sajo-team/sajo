package com.sajo.trading_service.trading.reconciliation;

import com.sajo.trading_service.trading.client.dto.response.KisOrderInquiryItem;

public record MatchResult(
        MatchStatus status,
        KisOrderInquiryItem item
) {

    public static MatchResult matched(
            KisOrderInquiryItem item
    ) {
        return new MatchResult(
                MatchStatus.MATCHED,
                item
        );
    }

    public static MatchResult notFound() {
        return new MatchResult(
                MatchStatus.NOT_FOUND,
                null
        );
    }

    public static MatchResult ambiguous() {
        return new MatchResult(
                MatchStatus.AMBIGUOUS,
                null
        );
    }
}