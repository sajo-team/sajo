package com.sajo.user_service.account.controller.query;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 실제 libs:common의 CommonSecurityAutoConfiguration + HeaderAuthenticationFilter가
// @PreAuthorize와 함께 정상 동작하는지 확인하기 위한 풀 컨텍스트 통합 테스트.
@SpringBootTest
@AutoConfigureMockMvc
class AccountAdminControllerSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Gateway가 내려주는 X-User-Id/X-User-Role 헤더가 ADMIN이면 200을 반환한다")
    void withAdminHeaders_ok() throws Exception {
        mockMvc.perform(get("/api/v1/admin/accounts/token-status")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("X-User-Role이 ADMIN이 아니면 403을 반환한다")
    void withNonAdminHeaders_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/accounts/token-status")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "USER"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("헤더가 없으면 403을 반환한다 (Gateway를 거치지 않은 요청, Spring Security 기본 익명 인증으로 처리됨)")
    void withoutHeaders_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/accounts/token-status"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("X-User-Id가 UUID 형식이 아니면 익명 처리되어 403을 반환한다")
    void withInvalidUserIdHeader_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/accounts/token-status")
                        .header("X-User-Id", "not-a-uuid")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isForbidden());
    }
}
