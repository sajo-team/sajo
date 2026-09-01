package com.other;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@DisplayName("GlobalExceptionHandler 응답 테스트")
class GlobalExceptionHandlerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    @DisplayName("요청 body 검증 실패 시 필드별 에러를 반환한다")
    void invalidRequestBody_returnsFieldErrors() throws Exception {
        mockMvc.perform(post("/validate-body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ValidationRequest(""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("COMMON_0001"))
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    @DisplayName("요청 파라미터 검증 실패 시 400을 반환한다")
    void invalidRequestParam_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/validate-param").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0001"));
    }

    @Test
    @DisplayName("BusinessException은 커스텀 메시지와 자기 에러코드를 그대로 사용한다")
    void businessException_usesCustomMessageAndItsOwnErrorCode() throws Exception {
        mockMvc.perform(get("/business-error"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("COMMON_0004"))
                .andExpect(jsonPath("$.message").value("hub id 5를 찾을 수 없습니다"));
    }

    @Test
    @DisplayName("예상 못한 예외는 500과 공통 에러코드를 반환한다")
    void unexpectedException_returnsInternalServerError() throws Exception {
        mockMvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("COMMON_9999"))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    @DisplayName("FeignApiException은 호출한 서비스의 status/errorCode/message를 그대로 반환한다")
    void feignApiException_passesThroughOriginalStatusAndErrorCode() throws Exception {
        mockMvc.perform(get("/feign-error"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_0001"))
                .andExpect(jsonPath("$.message").value("계좌를 찾을 수 없습니다"));
    }

    @Test
    @DisplayName("FeignApiException의 status가 표준 HttpStatus에 없으면 502로 대체한다")
    void feignApiException_fallsBackToBadGatewayOnNonStandardStatus() throws Exception {
        mockMvc.perform(get("/feign-error-non-standard-status"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorCode").value("CLIENT_CLOSED"));
    }
}
