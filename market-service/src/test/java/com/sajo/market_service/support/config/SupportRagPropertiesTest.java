package com.sajo.market_service.support.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SupportRagPropertiesTest {

    @Test
    void fallsBackToDefaultChatModelNameWhenNull() {
        SupportRagProperties properties = new SupportRagProperties("classpath:x.md", 3, null);

        assertThat(properties.chatModelName()).isEqualTo("gpt-4o-mini");
    }

    @Test
    void fallsBackToDefaultChatModelNameWhenBlank() {
        SupportRagProperties properties = new SupportRagProperties("classpath:x.md", 3, "   ");

        assertThat(properties.chatModelName()).isEqualTo("gpt-4o-mini");
    }

    @Test
    void keepsExplicitlyConfiguredChatModelName() {
        SupportRagProperties properties = new SupportRagProperties("classpath:x.md", 3, "gpt-4o");

        assertThat(properties.chatModelName()).isEqualTo("gpt-4o");
    }

    @Test
    void fallsBackToDefaultTopKWhenNullOrNotPositive() {
        assertThat(new SupportRagProperties("classpath:x.md", null, "gpt-4o-mini").topK()).isEqualTo(3);
        assertThat(new SupportRagProperties("classpath:x.md", 0, "gpt-4o-mini").topK()).isEqualTo(3);
        assertThat(new SupportRagProperties("classpath:x.md", -1, "gpt-4o-mini").topK()).isEqualTo(3);
    }

    @Test
    void keepsExplicitlyConfiguredTopK() {
        SupportRagProperties properties = new SupportRagProperties("classpath:x.md", 5, "gpt-4o-mini");

        assertThat(properties.topK()).isEqualTo(5);
    }
}
