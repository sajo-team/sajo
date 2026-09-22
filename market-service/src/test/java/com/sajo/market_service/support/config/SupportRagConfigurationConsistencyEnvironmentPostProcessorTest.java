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
    void doesNothingWhenRagIsEnabledBothModelSelectorsAreOpenAiAndApiKeyIsPresent() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("sajo.support.rag.enabled", "true")
                .withProperty("spring.ai.model.chat", "openai")
                .withProperty("spring.ai.model.embedding", "openai")
                .withProperty("spring.ai.openai.api-key", "sk-test-key");

        assertThatCode(() -> postProcessor.postProcessEnvironment(environment, null))
                .doesNotThrowAnyException();
    }

    @Test
    void failsFastWhenRagIsEnabledButChatModelSelectorIsNotOpenAi() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("sajo.support.rag.enabled", "true")
                .withProperty("spring.ai.model.chat", "none")
                .withProperty("spring.ai.model.embedding", "openai")
                .withProperty("spring.ai.openai.api-key", "sk-test-key");

        assertThatIllegalStateException()
                .isThrownBy(() -> postProcessor.postProcessEnvironment(environment, null))
                .withMessageContaining("SPRING_AI_CHAT_MODEL");
    }

    @Test
    void failsFastWhenRagIsEnabledButEmbeddingModelSelectorIsNotOpenAi() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("sajo.support.rag.enabled", "true")
                .withProperty("spring.ai.model.chat", "openai")
                .withProperty("spring.ai.model.embedding", "none")
                .withProperty("spring.ai.openai.api-key", "sk-test-key");

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

    @Test
    void failsFastWhenModelSelectorsAreOpenAiButApiKeyIsMissing() {
        // 코드 리뷰로 지적된 잔여 SPOF: chat/embedding 셀렉터만 맞고 API 키가 비어 있으면
        // Spring AI 자동구성이 OpenAiChatModel/OpenAiEmbeddingModel 빈 생성 중 API 키 검증에서
        // 실패해 기동이 죽는다. 이 검증기가 그 전에 명확한 메시지로 먼저 잡아내야 한다.
        MockEnvironment environment = new MockEnvironment()
                .withProperty("sajo.support.rag.enabled", "true")
                .withProperty("spring.ai.model.chat", "openai")
                .withProperty("spring.ai.model.embedding", "openai");
        // spring.ai.openai.api-key는 의도적으로 설정하지 않는다(기본값 "").

        assertThatIllegalStateException()
                .isThrownBy(() -> postProcessor.postProcessEnvironment(environment, null))
                .withMessageContaining("OPENAI_API_KEY");
    }

    @Test
    void failsFastWhenModelSelectorsAreOpenAiButApiKeyIsBlank() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("sajo.support.rag.enabled", "true")
                .withProperty("spring.ai.model.chat", "openai")
                .withProperty("spring.ai.model.embedding", "openai")
                .withProperty("spring.ai.openai.api-key", "   ");

        assertThatIllegalStateException()
                .isThrownBy(() -> postProcessor.postProcessEnvironment(environment, null))
                .withMessageContaining("OPENAI_API_KEY");
    }
}
