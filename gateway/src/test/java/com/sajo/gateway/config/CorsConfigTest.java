package com.sajo.gateway.config;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CorsConfig 테스트")
class CorsConfigTest {

    private Filter corsFilter;

    @BeforeEach
    void setUp() {
        FilterRegistrationBean<?> bean = new CorsConfig().corsFilter();
        corsFilter = bean.getFilter();
    }

    @Test
    @DisplayName("허용된 origin(sajostock.site)은 Access-Control-Allow-Origin 헤더와 함께 통과한다")
    void allowedOriginGetsCorsHeaders() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/accounts");
        request.addHeader("Origin", "https://sajostock.site");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // when
        corsFilter.doFilter(request, response, chain);

        // then
        assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo("https://sajostock.site");
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("허용된 www 서브도메인(www.sajostock.site)도 Access-Control-Allow-Origin 헤더와 함께 통과한다")
    void allowedWwwOriginGetsCorsHeaders() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/accounts");
        request.addHeader("Origin", "https://www.sajostock.site");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // when
        corsFilter.doFilter(request, response, chain);

        // then
        assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo("https://www.sajostock.site");
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("허용되지 않은 origin은 Access-Control-Allow-Origin 헤더 없이 거부된다")
    void disallowedOriginIsRejectedWithoutCorsHeaders() throws Exception {
        // given - 우리 프론트가 아닌 임의의 출처
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/accounts");
        request.addHeader("Origin", "https://evil.example.com");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // when
        corsFilter.doFilter(request, response, chain);

        // then
        assertThat(response.getHeader("Access-Control-Allow-Origin")).isNull();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("허용된 origin의 preflight(OPTIONS)는 Allow-Methods/Allow-Headers를 포함해 응답하고 체인을 종료한다")
    void allowedOriginPreflightRespondsAndStopsChain() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/accounts");
        request.addHeader("Origin", "https://sajostock.site");
        request.addHeader("Access-Control-Request-Method", "POST");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // when
        corsFilter.doFilter(request, response, chain);

        // then - CorsFilter가 preflight 응답을 직접 쓰고 체인을 끊으므로 downstream까지 가지 않는다
        assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo("https://sajostock.site");
        assertThat(response.getHeader("Access-Control-Allow-Methods")).contains("POST");
        assertThat(chain.getRequest()).isNull();
    }

    // 코드 리뷰 반영 - Access-Control-Max-Age 미설정 시 브라우저가 매 요청마다 preflight를
    // 다시 보낼 수 있다는 지적. 값이 실제로 응답 헤더에 실리는지 확인한다.
    @Test
    @DisplayName("preflight 응답에 Access-Control-Max-Age가 포함되어 브라우저가 캐시할 수 있다")
    void preflightResponseIncludesMaxAge() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/accounts");
        request.addHeader("Origin", "https://sajostock.site");
        request.addHeader("Access-Control-Request-Method", "POST");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // when
        corsFilter.doFilter(request, response, chain);

        // then
        assertThat(response.getHeader("Access-Control-Max-Age")).isEqualTo("3600");
    }
}