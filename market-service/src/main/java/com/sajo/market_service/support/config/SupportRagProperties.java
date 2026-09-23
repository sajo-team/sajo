package com.sajo.market_service.support.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sajo.support.rag")
public record SupportRagProperties(
        String documentPath,
        Integer topK,
        String chatModelName
) {

    private static final String DEFAULT_CHAT_MODEL_NAME = "gpt-4o-mini";

    public SupportRagProperties {
        topK = (topK == null || topK <= 0) ? 3 : topK;
        // 모델명을 코드에 하드코딩하는 대신 프로퍼티로 분리한다. OpenAI가 모델을 폐지(deprecate)해
        // 교체가 필요해지는 경우, 코드 수정 없이 이 값(SUPPORT_RAG_CHAT_MODEL_NAME)만 바꿔서
        // 재배포할 수 있도록 하기 위함이다. reasoning 모델(gpt-5-mini 등)이 Spring AI의 기본값으로
        // 잡히면서 챗 완성 호출 하나에만 6초 이상 걸렸던 문제를 gpt-4o-mini로 고정해 해결했는데
        // (Zipkin 트레이스로 실측 확인), 그 값이 바뀔 수 있다는 전제를 여기서도 유지한다.
        chatModelName = (chatModelName == null || chatModelName.isBlank()) ? DEFAULT_CHAT_MODEL_NAME : chatModelName;
    }
}
