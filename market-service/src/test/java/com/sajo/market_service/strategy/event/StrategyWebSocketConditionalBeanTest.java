package com.sajo.market_service.strategy.event;

import com.sajo.market_service.market.config.MarketWebSocketProperties;
import com.sajo.market_service.market.websocket.KisWebSocketClient;
import com.sajo.market_service.strategy.repository.query.StrategyQueryRepository;
import com.sajo.market_service.strategy.runner.StrategySubscriptionInitializer;
import com.sajo.market_service.strategy.service.command.StrategyEvaluationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * market.websocket.enabled=false(기본값)인 환경에서도 애플리케이션이 정상적으로 뜨는지,
 * WebSocket 연동 컴포넌트(실제 클래스, 재선언 아님)가 클래스에 붙은
 * {@code @ConditionalOnProperty(market.websocket.enabled=true)} 조건대로만 등록되는지 검증한다.
 * 전체 Spring Boot 컨텍스트(DB 등) 없이 대상 클래스만 좁혀서 확인한다.
 */
class StrategyWebSocketConditionalBeanTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestCollaborators.class)
            .withConfiguration(UserConfigurations.of(
                    StrategyStockSubscriptionListener.class,
                    StrategySubscriptionInitializer.class
            ));

    @Test
    @DisplayName("market.websocket.enabled 설정이 없으면(기본값 false) 관련 빈이 생성되지 않는다")
    void doesNotCreateBeansWhenWebSocketDisabled() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(StrategyStockSubscriptionListener.class);
            assertThat(context).doesNotHaveBean(StrategySubscriptionInitializer.class);
        });
    }

    @Test
    @DisplayName("market.websocket.enabled=true면 관련 빈이 정상적으로 생성된다")
    void createsBeansWhenWebSocketEnabled() {
        contextRunner.withPropertyValues("market.websocket.enabled=true").run(context -> {
            assertThat(context).hasSingleBean(StrategyStockSubscriptionListener.class);
            assertThat(context).hasSingleBean(StrategySubscriptionInitializer.class);
        });
    }

    @Configuration
    static class TestCollaborators {
        @Bean
        KisWebSocketClient kisWebSocketClient() {
            return Mockito.mock(KisWebSocketClient.class);
        }

        @Bean
        StrategyEvaluationService strategyEvaluationService() {
            return Mockito.mock(StrategyEvaluationService.class);
        }

        @Bean
        StrategyQueryRepository strategyQueryRepository() {
            return Mockito.mock(StrategyQueryRepository.class);
        }

        @Bean
        MarketWebSocketProperties marketWebSocketProperties() {
            return new MarketWebSocketProperties(
                    false, null, null, null, null, 2.0, java.util.List.of(), null, 0
            );
        }
    }
}
