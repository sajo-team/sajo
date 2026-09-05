package com.sajo.common.jwt.config;

import com.sajo.common.jwt.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

// JwtTokenProvider를 공용 빈으로 등록. user-service(발급)/gateway(검증) 둘 다 사용
// sajo.jwt.secret이 설정된 서비스에서만 활성화 - 아직 이 값을 안 쓰는 다른 서비스의
// 컨텍스트 로딩까지 깨뜨리지 않기 위함
@AutoConfiguration
@ConditionalOnProperty(prefix = "sajo.jwt", name = "secret")
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
