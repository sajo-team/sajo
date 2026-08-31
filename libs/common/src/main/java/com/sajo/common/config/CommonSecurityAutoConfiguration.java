package com.sajo.common.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 임시 Security 설정. 담당: 우태
 *
 * spring-boot-starter-security가 클래스패스에 있으면 커스텀 SecurityFilterChain이
 * 없을 때 스프링부트가 자동으로 기본 로그인 화면 + Basic Auth를 걸어버립니다.
 * 그래서 이 라이브러리를 의존하는 서비스들이 JWT 필터를 실제로 넣기 전까지는
 * permitAll()로 임시로 열어둡니다.
 *
 * @ConditionalOnMissingBean이라 실제 SecurityFilterChain을 구현해서 등록하면
 * 이 자동설정은 자동으로 비활성화됩니다 - 이 파일을 지울 필요 없이 교체됩니다.
 *
 * TODO: JWT 인증 필터 추가, 인증/인가 규칙 구현.
 */
@AutoConfiguration
@ConditionalOnClass(SecurityFilterChain.class)
@ConditionalOnMissingBean(SecurityFilterChain.class)
public class CommonSecurityAutoConfiguration {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
