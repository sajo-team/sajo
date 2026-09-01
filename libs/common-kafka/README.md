# common-kafka

sajo 프로젝트 서비스들이 공통으로 쓰는 Kafka 라이브러리. `java-library`라 부팅 안 됨 — 다른 서비스가 의존성으로 가져다 씀.

## 의존성 추가

```groovy
// 서비스 build.gradle
dependencies {
    implementation project(':libs:common-kafka')
}
```

`AutoConfiguration.imports` 기반이라 서비스가 따로 스캔 범위에 넣을 필요 없음. 의존성 + Kafka 연결 정보(`spring.kafka.bootstrap-servers` 등)만 있으면 아래가 전부 자동 등록됨.

## 뭐가 자동으로 켜지는지 (`CommonKafkaAutoConfiguration`)

- `DefaultKafkaProducerFactoryCustomizer` — Boot가 만드는 `DefaultKafkaProducerFactory`의 value serializer를 `JacksonJsonSerializer`로 교체
- `DefaultKafkaConsumerFactoryCustomizer` — 마찬가지로 consumer factory의 value deserializer를 `ErrorHandlingDeserializer`(내부에서 `JacksonJsonDeserializer`를 감쌈)로 교체
- 둘 다 커스터마이저 방식이라 Boot가 만드는 `KafkaTemplate` / `ConcurrentKafkaListenerContainerFactory` 등 나머지 자동 설정은 그대로 유지되고, value 직렬화 부분만 갈아끼워짐

## 직렬화 방식

- value: `JacksonJsonSerializer` / `JacksonJsonDeserializer` — Jackson 기반 JSON 직렬화
- `deserializer.trustedPackages("com.sajo.*")` — 이 모노레포의 서비스/DTO가 전부 `com.sajo.*` 밑에 있는 컨벤션을 그대로 신뢰 경계로 삼음. Kafka 브로커 자체가 사내 인프라 안에서만 도는 내부 메시지라는 전제 + 실제 패키지 컨벤션을 근거로 좁혀둔 값 — 외부 회사/외부 시스템이 프로듀서로 붙는 경로가 생기면 이 전제가 깨지니 그 토픽만큼은 별도로 좁히거나(`trustedPackages` 재조정) 타입을 코드로 고정(`new JacksonJsonDeserializer<>(YourDto.class, jsonMapper)`)하는 걸 검토해야 함
- 역직렬화 실패(poison pill) 방어: `JacksonJsonDeserializer`를 `ErrorHandlingDeserializer`로 감싸둠 — 안 감싸면 역직렬화 실패가 `poll()` 단계에서 바로 터져서 `CommonErrorHandler`(재시도/DLT 로직)가 아예 못 잡고, 컨슈머가 같은 offset에서 무한 재시도하며 그 파티션 전체가 멈추는 사고로 이어짐. `ErrorHandlingDeserializer`는 실패를 `DeserializationException`으로 미뤄서 리스너 레벨 에러 핸들러가 정상적으로 처리(스킵/DLT)하게 해줌
- key는 이 라이브러리에서 별도로 안 건드림 — Boot 기본(`StringSerializer`/`StringDeserializer`) 그대로 씀. `spring.kafka.bootstrap-servers`만 있으면 key 쪽은 서비스가 따로 설정 안 해도 됨(`KafkaIntegrationTest`가 bootstrap-servers 외엔 아무 프로퍼티도 안 주고 통과하는 걸로 확인됨) — key를 String이 아닌 다른 타입으로 쓰고 싶은 서비스만 `spring.kafka.*.key-serializer`/`key-deserializer`를 직접 오버라이드

```
KafkaTemplate<String, Object> template;
template.send("order-created", new OrderCreatedEvent(orderId, ...)); // value가 JSON으로 직렬화됨, 타입 그대로 복원 가능
```

## 에러 핸들링 (Retry / DLT)

Retry(재시도)와 DLT는 성격이 다른 결정이라 나눠서 다룸:

- **Retry**: 넣어도 서비스에 부담이 없음 — 실패하면 몇 번 더 시도하고 멈추는 것뿐. 그래서 **라이브러리가 기본값으로 깔아줌**.
- **DLT**: 실패 메시지를 별도 토픽으로 보내는 순간 "그 토픽을 누가 소비/모니터링하냐"는 운영 책임이 생김. 아무도 안 보는 DLT는 메시지 무덤이 될 뿐이라, 라이브러리가 강제로 켜주지 않고 **필요한 서비스가 직접 선택**하게 함.

### 기본 재시도 (자동으로 켜짐)

`CommonErrorHandler` 빈을 서비스가 하나도 등록 안 하면 `CommonKafkaAutoConfiguration`이 아래 기본값을 깔아줌 (`@ConditionalOnMissingBean(CommonErrorHandler.class)`):

- 재시도 3회, 간격 1초 (`FixedBackOff`)
- recoverer 없음 (DLT 없음) — 재시도 다 실패하면 `ERROR` 레벨로 로그만 남기고 넘어감
- `DeserializationException`(poison pill)은 `addNotRetryableExceptions`로 재시도 대상에서 제외해둠 — 역직렬화 실패는 재시도해도 매번 똑같이 실패할 게 뻔하니, 재시도 없이 바로 로그만 남기고 스킵(`KafkaErrorHandlers.withDlt()`를 쓰는 경우엔 바로 DLT로)
- 프로퍼티로 조정 가능:

```yaml
sajo:
  kafka:
    error:
      retry-interval-ms: 1000  # 기본 1000
      retry-count: 3           # 기본 3
```

참고로 **아무 설정도 없이 이 라이브러리조차 안 붙였을 때** Spring Kafka 자체 fallback은 `FixedBackOff(0, 9)`(0ms 간격으로 9번) + recoverer 없음이라, 이 기본값이 그보다 안전함(백오프 있는 3회 + 명확한 로그).

### DLT가 필요하면 (`KafkaErrorHandlers.withDlt`)

DLT까지 필요한 서비스는 라이브러리 기본값 대신 직접 `CommonErrorHandler` 빈을 등록해서 오버라이드함 — `KafkaErrorHandlers`가 그 조립을 도와주는 헬퍼만 제공함(자동 등록 안 됨):

```java
@Bean
public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> template) {
    return KafkaErrorHandlers.withDlt(template); // 원본토픽 + ".DLT" 토픽으로 재발행, 재시도 3회/1초
}
```

재시도 간격/횟수를 기본값(1초/3회)과 다르게 쓰고 싶으면 오버로드로 직접 넘기면 됨 — `defaultKafkaErrorHandler`와 같은 프로퍼티(`sajo.kafka.error.*`)를 그대로 재사용해도 되고, 완전히 다른 값을 써도 됨:

```java
@Bean
public CommonErrorHandler kafkaErrorHandler(
        KafkaTemplate<Object, Object> template,
        @Value("${sajo.kafka.error.retry-interval-ms:1000}") long retryIntervalMs,
        @Value("${sajo.kafka.error.retry-count:3}") long retryCount) {
    return KafkaErrorHandlers.withDlt(template, retryIntervalMs, retryCount);
}
```

DLT 토픽 이름 컨벤션(`{원본토픽}.DLT`)은 라이브러리가 제안하는 것뿐, 그 토픽을 실제로 모니터링/처리하는 책임은 등록한 서비스가 짐.

### `@RetryableTopic`과 같이 쓸 때 주의

`@RetryableTopic`은 리스너 메서드에 직접 붙이는 애노테이션이라 라이브러리가 기본으로 깔아줄 수 있는 게 아님 — 필요한 리스너에 서비스가 직접 붙이면 됨. 단, **컨테이너 레벨 `CommonErrorHandler`(블로킹 재시도)와 `@RetryableTopic`(토픽 기반 논블로킹 재시도)을 같은 리스너에 무심코 같이 걸면 이중 재시도가 남** — 둘 다 쓰려면 `@RetryableTopic`이 지원하는 "블로킹+논블로킹 조합" 설정(`RetryTopicConfigurationBuilder`의 `notRetryOn`/`blockingRetries` 계열)으로 명시적으로 역할을 나눠야 함.

