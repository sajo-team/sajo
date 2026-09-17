package com.sajo.operation_service.controller;

import com.sajo.common.code.GeneralResponseCode;
import com.sajo.common.response.GeneralResponse;
import com.sajo.operation_service.controller.dto.request.AlertManagerWebhookRequest;
import com.sajo.operation_service.controller.dto.response.AlertWebhookAcceptedResponse;
import com.sajo.operation_service.service.AlertAnalysisAsyncProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/operations")
@RequiredArgsConstructor
public class AlertWebhookController {

    private final AlertAnalysisAsyncProcessor alertAnalysisAsyncProcessor;

    @PostMapping("/webhook")
    public ResponseEntity<GeneralResponse<AlertWebhookAcceptedResponse>> receive(
            @RequestBody AlertManagerWebhookRequest request
    ) {

        alertAnalysisAsyncProcessor.process(request);

        return GeneralResponse.toResponseEntity(
                GeneralResponseCode.ACCEPTED,
                new AlertWebhookAcceptedResponse(request.alerts().size())
        );
    }
}
