package com.sajo.trading_service.trading.controller;

import com.sajo.common.code.GeneralResponseCode;
import com.sajo.common.response.GeneralResponse;
import com.sajo.common.response.PageResponse;
import com.sajo.trading_service.trading.controller.dto.response.ExecutionResponse;
import com.sajo.trading_service.trading.service.query.ExecutionQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/executions")
public class ExecutionController {
    private final ExecutionQueryService executionQueryService;


    @GetMapping
    public ResponseEntity<GeneralResponse<PageResponse<ExecutionResponse>>> getExecutions(
            @RequestHeader("X-User-Id")UUID userId,
            Pageable pageable
    ) {
        Page<ExecutionResponse> page =
                executionQueryService.findExecutionsByUserId(
                        userId,
                        pageable
                );

        PageResponse<ExecutionResponse> response =
                PageResponse.from(page);

        return GeneralResponse.toResponseEntity(
                GeneralResponseCode.OK,
                response
        );
    }

    @GetMapping("/{executionId}")
    public ResponseEntity<GeneralResponse<ExecutionResponse>> getExecutionDetail(
            @PathVariable UUID executionId,
            @RequestHeader("X-User-Id") UUID userId
    ) {
        ExecutionResponse response =
                executionQueryService.findExecutionByIdAndUserId(
                        executionId,
                        userId
                );

        return GeneralResponse.toResponseEntity(
                GeneralResponseCode.OK,
                response
        );
    }
}
