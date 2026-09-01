# common-redis

sajo 프로젝트 서비스들이 공통으로 쓰는 Redis 라이브러리. `java-library`라 부팅 안 됨 — 다른 서비스가 의존성으로 가져다 씀.

## 의존성 추가

```groovy
// 서비스 build.gradle
dependencies {
    implementation project(':libs:common-redis')
}
```

`AutoConfiguration.imports` 기반이라 서비스가 따로 스캔 범위에 넣을 필요 없음. 의존성 + Redis 연결 정보(`spring.data.redis.host` 등)만 있으면 아래가 전부 자동 등록됨.

## 뭐가 자동으로 켜지는지 (`CommonRedisAutoConfiguration`)

- `redisTemplate` (`RedisTemplate<String, Object>`) — Boot가 기본으로 만드는 JDK 직렬화 `redisTemplate` 빈 대신 이걸 씀
- `cacheManager` (`RedisCacheManager`) + `@EnableCaching` — 서비스가 `@EnableCaching` 따로 안 붙여도 `@Cacheable`/`@CacheEvict` 바로 사용 가능
- 둘 다 `@ConditionalOnMissingBean`이라 서비스가 직접 빈을 정의하면 그게 우선함

## 직렬화 방식

- key: `StringRedisSerializer`
- value: `GenericJacksonJsonRedisSerializer` — JSON에 실제 클래스 타입 정보를 같이 넣어서, 꺼낼 때 원래 타입 그대로 복원됨
- 타입 정보 기반 역직렬화(polymorphic deserialization)라 아무 클래스나 복원해주면 위험함(역직렬화 gadget-chain 공격). 그래서 `com.sajo.` 패키지 하위 클래스만 허용하는 화이트리스트(`BasicPolymorphicTypeValidator`)를 적용함 — **캐시/RedisTemplate에 저장하는 값의 클래스는 반드시 `com.sajo.*` 패키지 밑에 있어야 함**, 아니면 읽어올 때 `InvalidTypeIdException` 남

```java
record ItemView(String name, int stock) {}
```

```
redisTemplate.opsForValue().set("item:1", new ItemView("hub-a", 3));
ItemView view = (ItemView) redisTemplate.opsForValue().get("item:1"); // 캐스팅 없이 바로 써도 되지만 타입은 이렇게 유지됨
```

## 캐싱 (`@Cacheable` 등)

서비스 쪽 `@EnableCaching` 없이 그냥 표준 Spring Cache 어노테이션을 쓰면 됨:

```java
@Cacheable(cacheNames = "item", key = "#id")
public ItemView getItem(Long id) {
    return itemRepository.findView(id);
}
```

- `null` 값은 캐싱 안 함 (`disableCachingNullValues()`)
- TTL을 서비스가 직접 `CacheManager` 빈을 등록해서 통째로 오버라이드할 수도 있음 (`@ConditionalOnMissingBean`이라 자동으로 이쪽이 빠짐)

### TTL 설정 (`sajo.redis.cache.ttl`)

캐시 TTL은 `application.yml`의 `sajo.redis.cache.ttl` 맵으로 캐시 이름(`cacheNames`)별로 다르게 줄 수 있음:

```yaml
sajo:
  redis:
    cache:
      ttl:
        default: 10m   # ttl 맵에 없는 캐시 이름 전부에 적용되는 기본값
        item: 5m        # cacheNames = "item" 인 캐시만 5분
        order: 30s      # cacheNames = "order" 인 캐시만 30초
```

- `default` 키는 예약어로, 맵에 없는 캐시 이름에 적용되는 기본 TTL을 정함 — 안 주면 **10분**으로 설정
- 나머지 키는 그 이름과 정확히 같은 `cacheNames`에만 적용됨 (`@Cacheable(cacheNames = "item")` ↔ `ttl.item`)
- 서비스가 프로퍼티를 아예 안 주면 예전처럼 전체 10분 고정 TTL과 동일하게 동작함 (하위 호환)

## 캐시 키 네임스페이스

여러 서비스가 같은 Redis를 공유하는 경우, 서로 다른 서비스가 우연히 같은 `cacheNames`(예: `"item"`)를 쓰면 캐시가 섞일 수 있음. 그래서 캐시 키 앞에 `spring.application.name` 값을 자동으로 붙임.

```
market-service:item::42   (market-service가 저장한 캐시)
trading-service:item::42  (trading-service가 저장한 캐시 — 같은 cacheNames "item"이어도 안 겹침)
```

- 서비스는 `application.yml`에 `spring.application.name`만 설정하면 됨 (Eureka 등록에도 어차피 필요한 값이라 별도 설정 부담 없음)
- 값이 없으면 `"application"`으로 fallback (`@Value("${spring.application.name:application}")`)
- 이 prefix는 `@Cacheable` 등 **캐시 추상화를 거치는 키에만** 적용됨. `redisTemplate.opsForValue().set(...)`처럼 직접 키를 다루는 경우엔 안 붙으니, 그런 키는 서비스가 직접 네임스페이스를 붙이는 컨벤션을 사용
