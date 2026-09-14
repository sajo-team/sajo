package com.sajo.market_service.market.dto.kis;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KisApprovalKeyRequest(
        @JsonProperty("grant_type") String grantType,
        @JsonProperty("appkey") String appKey,
        @JsonProperty("secretkey") String secretKey
) {

    public static KisApprovalKeyRequest of(String appKey, String secretKey) {
        return new KisApprovalKeyRequest("client_credentials", appKey, secretKey);
    }
}
