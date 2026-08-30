package com.other.entity;

import com.other.TestApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@DisplayName("AuditorAware auto-configuration 테스트")
class AuditorAwareAutoConfigurationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    @DisplayName("X-User-Id 헤더만으로 서비스 설정 없이 createdBy가 채워진다")
    void createdByIsPopulatedFromXUserIdHeader_withNoServiceSideConfig() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/audit-test").header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(userId.toString()));
    }

    @Test
    @DisplayName("X-User-Id 헤더가 없으면 createdBy는 null이다")
    void createdByIsNullWhenHeaderMissing() throws Exception {
        mockMvc.perform(post("/audit-test"))
                .andExpect(status().isOk())
                .andExpect(content().string("null"));
    }
}
