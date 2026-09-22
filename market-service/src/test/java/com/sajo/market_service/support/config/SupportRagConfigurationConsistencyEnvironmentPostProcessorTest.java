package com.sajo.market_service.support.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class SupportRagConfigurationConsistencyEnvironmentPostProcessorTest {

    private final SupportRagConfigurationConsistencyEnvironmentPostProcessor postProcessor =
            new SupportRagConfigurationConsistencyEnvironmentPostProcessor();

    @Test
    void doesNothingWhenRagIsDisabled() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("sajo.support.rag.enabled", "false")
                .withProperty("spring.ai.model.chat", "none")
                .withProperty("spring.ai.model.embedding", "none");

        assertThatCode(() -> postProcessor.postProcessEnvironment(environment, null))
                .doesNotThrowAnyException();
    }

    @Test
    void doesNothingWhenRagIsDisabledByDefault() {
        // sajo.support.rag.enabled를 아예 지정하지 않은 경우(기본값 false)도 통과해야 한다.
        MockEnvironment environment = new MockEnvironment();

        assertThatCode(() -> postProcessor.postProcessEnvironment(environment, null))
                .doesNotThrowAnyException();
    }

    @Test
    void doesNothingWhenRagIsEnabledAndBothModelSelectorsAreOpenAi() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("sajo.support.rag.enabled", "true")
                .withProperty("spring.ai.model.chat", "openai")
                .withProperty("spring.ai.model.embedding", "openai");

        assertThatCode(() -> postProcessor.postProcessEnvironment(environment, null))
                .doesNotThrowAnyException();
    }

    @Test
    void failsFastWhenRagIsEnabledButChatModelSelectorIsNotOpenAi() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("sajo.support.rag.enabled", "true")
                .withProperty("spring.ai.model.chat", "none")
                .withProperty("spring.ai.model.embedding", "openai");

        assertThatIllegalStateException()
                .isThrownBy(() -> postProcessor.postProcessEnvironment(environment, null))
                .withMessageContaining("SPRING_AI_CHAT_MODEL");
    }

    @Test
    void failsFastWhenRagIsEnabledButEmbeddingModelSelectorIsNotOpenAi() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("sajo.support.rag.enabled", "true")
                .withProperty("spring.ai.model.chat", "openai")
                .withProperty("spring.ai.model.embedding", "none");

        assertThatIllegalStateException()
                .isThrownBy(() -> postProcessor.postProcessEnvironment(environment, null))
                .withMessageContaining("SPRING_AI_EMBEDDING_MODEL");
    }

    @Test
    void failsFastWhenRagIsEnabledButBothModelSelectorsAreMissingEntirely() {
        // spring.ai.model.chat/embedding을 아예 지정하지 않으면 기본값 "none"으로 취급되어야 한다.
        MockEnvironment environment = new MockEnvironment()
                .withProperty("sajo.support.rag.enabled", "true");

        assertThatIllegalStateException()
                .isThrownBy(() -> postProcessor.postProcessEnvironment(environment, null));
    }
}
