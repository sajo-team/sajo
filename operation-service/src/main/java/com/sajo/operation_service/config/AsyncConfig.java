package com.sajo.operation_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "alertAnalysisExecutor")
    public Executor alertAnalysisExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("alert-analysis-");

        // 큐+스레드가 다 차면 기본값(AbortPolicy)은 컨트롤러 호출 스레드로 예외를 던져 webhook
        // 자체를 실패시킴 - trading-service의 kisOrderExecutor와 동일하게 호출 스레드가 대신
        // 처리하도록 해서 Alertmanager에는 항상 202로 응답하게 한다
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        executor.initialize();

        return executor;
    }
}
