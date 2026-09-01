package com.other;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@DisplayName("CommonPageableArgumentResolver 테스트")
class CommonPageableArgumentResolverTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    @DisplayName("size, sort 파라미터 없으면 기본값(10, createdAt DESC)이 적용된다")
    void noParams_usesDefaults() throws Exception {
        mockMvc.perform(get("/pageable"))
                .andExpect(status().isOk())
                .andExpect(content().string("0,10,createdAt: DESC"));
    }

    @Test
    @DisplayName("허용되지 않은 size(예: 20)는 기본값 10으로 대체된다")
    void disallowedSize_fallsBackToDefault() throws Exception {
        mockMvc.perform(get("/pageable").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(content().string("0,10,createdAt: DESC"));
    }

    @Test
    @DisplayName("허용된 size(30)는 그대로 적용된다")
    void allowedSize_isUsed() throws Exception {
        mockMvc.perform(get("/pageable").param("size", "30"))
                .andExpect(status().isOk())
                .andExpect(content().string("0,30,createdAt: DESC"));
    }

    @Test
    @DisplayName("sort 파라미터를 명시하면 그 값이 기본 정렬 대신 사용된다")
    void explicitSort_overridesDefault() throws Exception {
        mockMvc.perform(get("/pageable").param("sort", "name,asc"))
                .andExpect(status().isOk())
                .andExpect(content().string("0,10,name: ASC"));
    }
}
