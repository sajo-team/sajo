package com.sajo.operation_service.controller;

import com.sajo.operation_service.controller.dto.request.AlertManagerWebhookRequest;
import com.sajo.operation_service.service.AlertAnalysisAsyncProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AlertWebhookController.class)
class AlertWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AlertAnalysisAsyncProcessor alertAnalysisAsyncProcessor;

    @Test
    @DisplayName("유효한 요청이면 202와 firing 개수를 반환하고 AlertAnalysisAsyncProcessor에 위임한다")
    void receive_validRequest_returns202AndDelegates() throws Exception {
        String requestBody = """
                {
                  "status": "firing",
                  "alerts": [
                    {
                      "status": "firing",
                      "labels": {"alertname": "HighCpuUsage", "application": "trading-service"},
                      "annotations": {"summary": "s", "description": "d"},
                      "startsAt": "2026-09-17T03:00:00Z",
                      "endsAt": "0001-01-01T00:00:00Z"
                    },
                    {
                      "status": "resolved",
                      "labels": {"alertname": "HighCpuUsage", "application": "trading-service"},
                      "annotations": {"summary": "s", "description": "d"},
                      "startsAt": "2026-09-17T02:00:00Z",
                      "endsAt": "2026-09-17T02:10:00Z"
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/operations/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.firingAlertCount").value(1));

        verify(alertAnalysisAsyncProcessor).process(any(AlertManagerWebhookRequest.class));
    }

    @Test
    @DisplayName("alerts가 비어있으면 400을 반환하고 AlertAnalysisAsyncProcessor를 호출하지 않는다")
    void receive_emptyAlerts_returns400AndDoesNotDelegate() throws Exception {
        String requestBody = """
                {
                  "status": "firing",
                  "alerts": []
                }
                """;

        mockMvc.perform(post("/api/v1/operations/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(alertAnalysisAsyncProcessor);
    }
}
