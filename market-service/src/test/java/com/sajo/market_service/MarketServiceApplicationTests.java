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
@TestPropertySource(properties = "sajo.support.rag.enabled=false")
class MarketServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
