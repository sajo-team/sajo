package com.sajo.gateway.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

// 프론트(sajostock.site)에서 이 gateway로 브라우저 fetch/axios 요청을 보낼 때 필요한
// CORS 허용 설정. gateway는 spring-cloud-starter-gateway-server-webmvc(서블릿 기반)라
// reactive gateway용 globalcors 프로퍼티가 아니라 일반 Spring MVC 방식의 CorsFilter를
// 직접 등록한다.
//
// 주의: 이 필터가 preflight(OPTIONS)에 정상 응답하려면 JwtAuthenticationFilter가 그
// 요청을 먼저 막아버리면 안 된다. 필터 등록 순서에 기대지 않도록
// JwtAuthenticationFilter.isPermitAll()에서도 "진짜 preflight"(Origin +
// Access-Control-Request-Method가 모두 있는 OPTIONS)는 통과시키게 처리해뒀다
// (그쪽 파일 참고, 리뷰 반영 - 단순 OPTIONS 전체 허용은 인증 우회 위험이 있어 좁힘).
// 여기 @Order(HIGHEST_PRECEDENCE)는 방어적으로 우선순위를 준 것뿐,
// 실제 preflight 통과의 핵심은 JwtAuthenticationFilter 쪽 수정이다.
@Configuration
public class CorsConfig {

    // 프론트 배포 도메인. www 유무에 따라 브라우저가 서로 다른 출처(origin)로 취급하고
    // 실제로 www.sajostock.site로 리다이렉트되는 게 확인돼서 두 버전 다 등록한다.
    private static final List<String> ALLOWED_ORIGINS = List.of(
            "https://sajostock.site",
            "https://www.sajostock.site"
    );

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(ALLOWED_ORIGINS);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.addExposedHeader("Authorization");
        // 쿠키가 아니라 Authorization 헤더로만 인증하는 구조라 credentials는 비활성으로 둔다.
        // (allowCredentials(true)를 쓰려면 allowedOrigins에 와일드카드를 쓸 수 없으니 주의)
        configuration.setAllowCredentials(false);
        // preflight 응답을 브라우저가 이 시간(초) 동안 캐시하게 해서, 같은 origin/method/header
        // 조합으로 매 요청마다 OPTIONS 왕복이 다시 발생하지 않도록 한다 - 리뷰 반영.
        // 크롬은 이 값이 아무리 커도 내부적으로 최대 2시간(7200초)로 자체 상한을 두므로
        // 3600(1시간)이면 충분하고, 값을 바꿔야 할 만큼 민감한 설정도 아니다.
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(new CorsFilter(source));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }
}