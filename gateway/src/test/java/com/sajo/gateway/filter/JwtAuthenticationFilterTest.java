package com.sajo.gateway.filter;
 
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajo.common.jwt.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
 
import java.util.UUID;
 
import static org.assertj.core.api.Assertions.assertThat;
 
@DisplayName("JwtAuthenticationFilter 테스트")
class JwtAuthenticationFilterTest {
 
    private static final String SECRET = "gateway-filter-test-secret-value-must-be-at-least-32-bytes";
 
    private JwtTokenProvider jwtTokenProvider;
    private JwtAuthenticationFilter filter;
 
    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(SECRET, 3600);
        filter = new JwtAuthenticationFilter(jwtTokenProvider, new ObjectMapper());
    }
 
    @Test
    @DisplayName("유효한 토큰이면 통과시키고, downstream에는 검증된 userId/role/sessionId로 헤더를 세팅한다")
    void validTokenSetsUserIdAndRoleHeaders() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        String sessionId = UUID.randomUUID().toString();
        String token = jwtTokenProvider.createAccessToken(userId, "ADMIN", sessionId);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/accounts");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // when
        filter.doFilter(request, response, chain);

        // then
        HttpServletRequest downstreamRequest = (HttpServletRequest) chain.getRequest();
        assertThat(downstreamRequest).isNotNull();
        assertThat(downstreamRequest.getHeader("X-User-Id")).isEqualTo(userId.toString());
        assertThat(downstreamRequest.getHeader("X-User-Role")).isEqualTo("ADMIN");
        assertThat(downstreamRequest.getHeader("X-Session-Id")).isEqualTo(sessionId);
    }

    // 다중 기기 로그인 지원 - 리뷰 반영: 클라이언트가 X-Session-Id를 직접 실어 보내도
    // 검증된 값으로 덮어써야 한다 (다른 세션인 척 사칭 방지)
    @Test
    @DisplayName("클라이언트가 X-Session-Id를 직접 실어 보내도 검증된 값으로 덮어쓴다 (세션 사칭 방지)")
    void clientSuppliedSessionIdIsOverridden() throws Exception {
        // given
        UUID userId = UUID.randomUUID();
        String realSessionId = UUID.randomUUID().toString();
        String spoofedSessionId = UUID.randomUUID().toString();
        String token = jwtTokenProvider.createAccessToken(userId, "USER", realSessionId);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/accounts");
        request.addHeader("Authorization", "Bearer " + token);
        request.addHeader("X-Session-Id", spoofedSessionId);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // when
        filter.doFilter(request, response, chain);

        // then
        HttpServletRequest downstreamRequest = (HttpServletRequest) chain.getRequest();
        assertThat(downstreamRequest.getHeader("X-Session-Id")).isEqualTo(realSessionId);
    }
 
    @Test
    @DisplayName("클라이언트가 X-User-Id/X-User-Role을 직접 실어 보내도 검증된 값으로 덮어쓴다 (스푸핑 방지)")
    void clientSuppliedHeadersAreOverridden() throws Exception {
        // given
        UUID realUserId = UUID.randomUUID();
        UUID spoofedUserId = UUID.randomUUID();
        String token = jwtTokenProvider.createAccessToken(realUserId, "USER");
 
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/accounts");
        request.addHeader("Authorization", "Bearer " + token);
        request.addHeader("X-User-Id", spoofedUserId.toString());
        request.addHeader("X-User-Role", "ADMIN"); // 일반 유저 토큰인데 헤더로 관리자 사칭 시도
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
 
        // when
        filter.doFilter(request, response, chain);
 
        // then
        HttpServletRequest downstreamRequest = (HttpServletRequest) chain.getRequest();
        assertThat(downstreamRequest.getHeader("X-User-Id")).isEqualTo(realUserId.toString());
        assertThat(downstreamRequest.getHeader("X-User-Role")).isEqualTo("USER");
    }
 
    @Test
    @DisplayName("Authorization 헤더가 없으면 401을 반환하고 체인을 진행하지 않는다")
    void missingAuthorizationHeaderReturnsUnauthorized() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/accounts");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
 
        // when
        filter.doFilter(request, response, chain);
 
        // then
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("COMMON_0002");
        assertThat(chain.getRequest()).isNull();
    }
 
    @Test
    @DisplayName("만료되거나 위조된 토큰이면 401을 반환한다")
    void invalidTokenReturnsUnauthorized() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/accounts");
        request.addHeader("Authorization", "Bearer invalid.token.value");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
 
        // when
        filter.doFilter(request, response, chain);
 
        // then
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }
 
    @Test
    @DisplayName("로그인 API는 토큰 없이 통과하고, 클라이언트가 보낸 X-User-Id/X-User-Role은 제거된다")
    void loginEndpointIsPermitAllAndStripsClientHeaders() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.addHeader("X-User-Id", UUID.randomUUID().toString());
        request.addHeader("X-User-Role", "ADMIN");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
 
        // when
        filter.doFilter(request, response, chain);
 
        // then
        HttpServletRequest downstreamRequest = (HttpServletRequest) chain.getRequest();
        assertThat(downstreamRequest).isNotNull();
        assertThat(downstreamRequest.getHeader("X-User-Id")).isNull();
        assertThat(downstreamRequest.getHeader("X-User-Role")).isNull();
    }

    // 리뷰 반영 - access token이 만료된 상태에서 호출되는 게 refresh의 정상 흐름이므로
    // 이 필터 단계에서 막히면 안 된다. 이게 permitAll에서 빠지면 Gateway가 만료된
    // 토큰을 보고 먼저 401을 반환해서 refresh 자체가 영영 호출될 수 없다.
    @Test
    @DisplayName("토큰 재발급(POST /api/v1/auth/refresh)은 토큰 없이 통과한다")
    void refreshEndpointIsPermitAll() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/refresh");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // when
        filter.doFilter(request, response, chain);

        // then
        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isNotEqualTo(401);
    }

    // 로그아웃은 인증된 사용자만 호출해야 하므로 permitAll에 들어가면 안 된다 -
    // 반대 방향(의도적으로 permitAll이 아닌 것)도 함께 확인해둔다.
    @Test
    @DisplayName("로그아웃(POST /api/v1/auth/logout)은 permitAll이 아니라 토큰이 필요하다")
    void logoutEndpointRequiresToken() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/logout");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        // when
        filter.doFilter(request, response, chain);

        // then
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("회원가입(POST /api/v1/users)은 토큰 없이 통과한다")
    void signUpEndpointIsPermitAll() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/users");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
 
        // when
        filter.doFilter(request, response, chain);
 
        // then
        assertThat(chain.getRequest()).isNotNull();
    }
 
    @Test
    @DisplayName("permitAll 목록은 path만이 아니라 method도 일치해야 한다 (예: GET /api/v1/users는 인증 필요)")
    void permitAllIsMethodSpecific() throws Exception {
        // given - POST /api/v1/users는 permitAll이지만 GET /api/v1/users는 아니어야 한다
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
 
        // when
        filter.doFilter(request, response, chain);
 
        // then
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }
 
    @Test
    @DisplayName("actuator 헬스체크는 토큰 없이 통과한다 (Prometheus/헬스체크용)")
    void actuatorEndpointIsPermitAll() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
 
        // when
        filter.doFilter(request, response, chain);
 
        // then
        assertThat(chain.getRequest()).isNotNull();
    }
 
    @Test
    @DisplayName("actuator라도 명시적으로 허용하지 않은 엔드포인트는 인증이 필요하다 (와일드카드 아님)")
    void unlistedActuatorEndpointRequiresAuth() throws Exception {
        // given - health/prometheus 외의 actuator 경로는 permitAll이 아니어야 함
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/env");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
 
        // when
        filter.doFilter(request, response, chain);
 
        // then
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }
 
    @Test
    @DisplayName("role 클레임이 없는(도입 이전 발급) 토큰이면 X-User-Role 헤더가 세팅되지 않는다")
    void tokenWithoutRoleClaimOmitsRoleHeader() throws Exception {
        // given - role 없이 발급된 토큰 (과거 발급분 상황 재현)
        UUID userId = UUID.randomUUID();
        String token = jwtTokenProvider.createAccessToken(userId, null);
 
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/accounts");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
 
        // when
        filter.doFilter(request, response, chain);
 
        // then
        HttpServletRequest downstreamRequest = (HttpServletRequest) chain.getRequest();
        assertThat(downstreamRequest.getHeader("X-User-Id")).isEqualTo(userId.toString());
        assertThat(downstreamRequest.getHeader("X-User-Role")).isNull();
        assertThat(downstreamRequest.getHeader("X-Session-Id")).isNull();
    }
}
