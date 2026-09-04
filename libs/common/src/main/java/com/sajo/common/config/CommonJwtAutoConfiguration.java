package com.sajo.common.config;

import com.sajo.common.jwt.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

// JwtTokenProvider를 공용 빈으로 등록. user-service(발급)/gateway(검증) 둘 다 사용
@AutoConfiguration
public class CommonJwtAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(JwtTokenProvider.class)
    public JwtTokenProvider jwtTokenProvider(
            @Value("${sajo.jwt.secret}") String secret,
            @Value("${sajo.jwt.access-token-validity-seconds}") long accessTokenValiditySeconds
    ) {
        return new JwtTokenProvider(secret, accessTokenValiditySeconds);
    }
}