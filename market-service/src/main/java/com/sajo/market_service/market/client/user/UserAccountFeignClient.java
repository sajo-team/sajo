package com.sajo.market_service.market.client.user;

import com.sajo.market_service.market.client.user.dto.UserKisTokenResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.UUID;

@FeignClient(name = "user-service", path = "/internal/v1/accounts")
public interface UserAccountFeignClient {

    @PostMapping("/{userId}/token")
    UserKisTokenResponse getKisToken(@PathVariable("userId") UUID userId);
}
