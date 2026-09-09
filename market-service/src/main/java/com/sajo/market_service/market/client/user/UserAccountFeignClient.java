package com.sajo.market_service.market.client.user;

import com.sajo.market_service.market.client.user.dto.UserKisTokenResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.UUID;

@FeignClient(name = "user-service")
public interface UserAccountFeignClient {

    @PostMapping("/internal/v1/accounts/{userId}/token")
    UserKisTokenResponse getKisToken(@PathVariable("userId") UUID userId);
}
