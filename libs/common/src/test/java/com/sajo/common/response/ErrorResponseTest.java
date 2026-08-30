package com.sajo.common.response;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ErrorResponse 직렬화 테스트")
class ErrorResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("errors가 null이면 JSON에서 제외된다")
    void nullErrorsFieldIsExcludedFromJson() throws Exception {
        ErrorResponse response = new ErrorResponse(false, "COMMON_9999", "서버 내부 오류가 발생했습니다", null);

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).doesNotContain("\"errors\"");
    }

    @Test
    @DisplayName("errors가 있으면 JSON에 포함된다")
    void presentErrorsFieldIsIncludedInJson() throws Exception {
        ErrorResponse response = new ErrorResponse(false, "COMMON_0001", "입력값이 유효하지 않습니다", Map.of("name", "must not be blank"));

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"errors\"").contains("must not be blank");
    }
}
