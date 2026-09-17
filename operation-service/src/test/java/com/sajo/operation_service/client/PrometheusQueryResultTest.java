package com.sajo.operation_service.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PrometheusQueryResultTest {

    @Test
    @DisplayName("성공 결과는 successful()이 true이고 series 값을 프롬프트 텍스트에 담는다")
    void success_returnsSeriesInPromptText() {
        PrometheusQueryResult.Series series = new PrometheusQueryResult.Series(
                Map.of("application", "market-service"), "0.5"
        );
        PrometheusQueryResult result = PrometheusQueryResult.success("process_cpu_usage{...}", List.of(series));

        assertThat(result.successful()).isTrue();
        assertThat(result.toPromptText())
                .contains("query=process_cpu_usage{...}")
                .contains("labels={application=market-service}")
                .contains("value=0.5");
    }

    @Test
    @DisplayName("매칭되는 series가 없으면 데이터 없음으로 표시한다")
    void success_withEmptySeries_showsNoData() {
        PrometheusQueryResult result = PrometheusQueryResult.success("up{...}", List.of());

        assertThat(result.successful()).isTrue();
        assertThat(result.toPromptText()).contains("result=데이터 없음");
    }

    @Test
    @DisplayName("실패 결과는 successful()이 false이고 에러 메시지를 프롬프트 텍스트에 담는다")
    void failure_returnsErrorInPromptText() {
        PrometheusQueryResult result = PrometheusQueryResult.failure("bad_query{", "parse error");

        assertThat(result.successful()).isFalse();
        assertThat(result.toPromptText())
                .contains("query=bad_query{")
                .contains("error=parse error");
    }
}
