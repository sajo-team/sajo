package com.sajo.market_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test")
// sajo.support.rag.enabled의 기본값은 application.yaml에서 false지만, 그 기본값은
// OS 환경변수(SUPPORT_RAG_ENABLED)로 덮어쓸 수 있고 OS 환경변수는 application.yaml/
// application-test.yaml의 값보다 우선순위가 높다. 로컬 개발 환경에 SUPPORT_RAG_ENABLED=true가
// 설정돼 있어도(.env 등) 이 컨텍스트 로딩 테스트가 OpenAI 자격 증명 없이 항상 통과해야 하므로,
// @TestPropertySource(OS 환경변수보다 우선순위가 높음)로 명시적으로 꺼둔다.
//
// 같은 이유로 spring.ai.model.chat/embedding도 명시적으로 none으로 고정한다. 로컬 .env에
// SPRING_AI_CHAT_MODEL=openai / SPRING_AI_EMBEDDING_MODEL=openai가 들어 있고, 그 값이
// 쉘 환경변수로 노출돼 있으면(OS 환경변수가 application.yaml의 ${..:none} 기본값보다 우선하므로)
// 컨텍스트 로딩 시 OpenAI 자동구성이 여전히 활성화되어 API 키 없이 빈 생성이 실패한다.
//
// chat/embedding뿐 아니라 image/audio(speech, transcription)/moderation 셀렉터도 명시하지
// 않으면 classpath에 openai 스타터가 하나뿐이라는 이유로 Spring AI가 전부 자동으로 "openai"로
// 잡아, 그 자동구성들도 API 키 없이 빈 생성에 실패한다(실제로 openAiSdkAudioSpeechModel에서
// 이 문제가 발생했다). 이 테스트는 그 모델 타입들을 전혀 쓰지 않으므로 전부 명시적으로 none으로
// 고정한다.
@TestPropertySource(properties = {
		"sajo.support.rag.enabled=false",
		"spring.ai.model.chat=none",
		"spring.ai.model.embedding=none",
		"spring.ai.model.image=none",
		"spring.ai.model.moderation=none",
		"spring.ai.model.audio.speech=none",
		"spring.ai.model.audio.transcription=none"
})
class MarketServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
