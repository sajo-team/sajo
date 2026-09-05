package com.sajo.trading_service.trading.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Slf4j
@Configuration
@EnableAsync
@EnableScheduling
public class TradingAsyncConfiguration {

    @Bean(name = "kisOrderExecutor")
    public Executor kisOrderExecutor() {
        ThreadPoolTaskExecutor executor =
                new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("kis-order-");

        executor.setRejectedExecutionHandler(
                (task, threadPoolExecutor) ->
                        log.error(
                                "KIS 주문 비동기 작업 제출 거부. "
                                        + "activeCount={}, poolSize={}, queueSize={}",
                                threadPoolExecutor.getActiveCount(),
                                threadPoolExecutor.getPoolSize(),
                                threadPoolExecutor.getQueue().size()
                        )
        );

        executor.initialize();

        return executor;
    }
}