package com.sajo.operation_service.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DependencyMappingQueriesTest {

    @Test
    @DisplayName("upStatus 쿼리는 application 라벨로 up 메트릭을 조회한다")
    void upStatus_queriesByApplicationLabel() {
        String query = DependencyMappingQueries.upStatus("redis");

        assertThat(query)
                .contains("up{")
                .contains("application=\"redis\"");
    }
}
