# common

sajo 프로젝트 서비스들이 공통으로 쓰는 라이브러리. `java-library`라 부팅 안 됨 — 다른 서비스가 의존성으로 가져다 씀.

## 의존성 추가

```groovy
// 서비스 build.gradle
dependencies {
    implementation project(':libs:common')
}
```

`@Component` 스캔이 아니라 `AutoConfiguration.imports` 기반이라, 서비스가 `com.sajo.common`을 따로 스캔 범위에 넣을 필요 없음. 의존성만 추가하면 아래 auto-config들이 전부 자동 등록됨 (각각 이미 관련 빈이 있으면 자기 자신은 안 뜸 — `@ConditionalOnMissingBean`).

## 뭐가 자동으로 켜지는지

- `CommonExceptionAutoConfiguration` → `GlobalExceptionHandler`
- `CommonJpaAuditingAutoConfiguration` → `@EnableJpaAuditing`
- `CommonAuditorAwareAutoConfiguration` → `RequestHeaderAuditorAware` (`AuditorAware<UUID>` 빈)
- `CommonPageableAutoConfiguration` → `CommonPageableArgumentResolver` (`Pageable` size/sort 공통 처리)
- `CommonFeignAutoConfiguration` → Feign 헤더 전파 인터셉터, 공통 `ErrorDecoder`, 로그 레벨
- `CommonSecurityAutoConfiguration` → `HeaderAuthenticationFilter` + 기본 `SecurityFilterChain`

## 공통 응답 포맷

- `GeneralResponse<T>(success, message, data)` — 성공 응답
- `ErrorResponse(success, errorCode, message, errors)` — 실패 응답
- 둘 다 `toResponseEntity(...)` static factory만 있음

```java
@PostMapping
public ResponseEntity<GeneralResponse<ItemDto>> create(@RequestBody @Valid ItemRequest request) {
    ItemDto result = itemService.create(request);
    return GeneralResponse.toResponseEntity(GeneralResponseCode.CREATED, result);
}
```

`GeneralResponseCode`에 없는 성공 응답 코드가 필요하면, `ResponseCode`를 직접 구현하는 서비스 전용 enum을 만들면 됨 (`toResponseEntity`가 `GeneralResponseCode`가 아니라 `ResponseCode` 인터페이스를 받음):

```java
public enum ItemResponseCode implements ResponseCode {
    PARTIALLY_UPDATED(HttpStatus.OK, "일부 필드만 업데이트되었습니다")
    // ResponseCode 인터페이스(getStatus/getMessage) 구현
}
```

```
GeneralResponse.toResponseEntity(ItemResponseCode.PARTIALLY_UPDATED, result);
```

## 에러 처리

`GlobalExceptionHandler`가 아래를 이미 다 잡아서 `ErrorResponse`로 내려줌 — 컨트롤러에서 따로 try/catch 안 해도 됨:

- `MethodArgumentNotValidException` / `ConstraintViolationException` (`@Valid`/`@Validated` 검증 실패)
- `HttpMessageNotReadableException` (요청 body 파싱 실패)
- `HttpRequestMethodNotSupportedException`
- `AuthenticationException` / `AccessDeniedException`
- `BusinessException` (아래)
- 나머지 전부 → 500

서비스 도메인 에러는 `BusinessException` 던지면 됨. `ErrorCode`는 서비스가 자기 도메인용 enum을 직접 만들어서 씀 (`ErrorResponseCode`처럼 `ErrorCode` 인터페이스만 구현하면 됨 — enum이라 클래스 상속은 안 되고 인터페이스 구현만 가능해서 이렇게 설계됨):

```java
public enum ItemErrorCode implements ErrorCode {
    ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "ITEM_0001", "아이템을 찾을 수 없습니다")
    // ErrorCode 인터페이스(getStatus/getErrorCode/getMessage) 구현
}
```

```
throw new BusinessException(ItemErrorCode.ITEM_NOT_FOUND);
// 또는 메시지 오버라이드
throw new BusinessException(ItemErrorCode.ITEM_NOT_FOUND, "id=" + id);
```

## Entity / Auditing

- `BaseEntity` — `createdAt`, `createdBy`만 있음. 생성만 되고 수정/삭제 없는 row용 (감사 로그 등).
- `BaseUpdatableEntity extends BaseEntity` — `updatedAt`, `updatedBy`, `deletedAt`, `deletedBy`, `isDeleted()`, `softDelete(UUID)` 추가. 일반적인 도메인 엔티티는 이거 상속.
- `createdBy`/`updatedBy`는 요청의 `X-User-Id` 헤더(UUID)에서 자동으로 채워짐 — 서비스 쪽 설정 없이 그냥 `@CreatedBy`/`@LastModifiedBy` 필드가 알아서 채워짐. 헤더가 없으면(요청 컨텍스트 밖, 배치 등) `Optional.empty()`라 그냥 안 채워짐, 에러 안 남.

```java
@Entity
public class Item extends BaseUpdatableEntity {
    // id, name 등 도메인 필드만 추가하면 됨
}
```

## Pageable

컨트롤러에 `Pageable`을 파라미터로 받으면 아래가 자동으로 적용됨 — 서비스 쪽에서 따로 설정할 필요 없음:

- `size`: 10 / 30 / 50만 허용. 그 외 값(또는 생략)이면 기본값 10으로 대체됨
- `sort`: 안 넘기면 `createdAt DESC`가 기본 정렬. 넘기면 그 값 그대로 사용
  - `updatedAt`은 기본 정렬에 안 씀 — `BaseEntity`만 상속한(수정 없는) 엔티티엔 그 필드가 없어서

응답 쪽은 `Page<T>`를 그대로 반환하지 말고 `PageResponse<T>`로 감싸서 내려줌 — `content`/`page`/`size`/`totalElements`/`totalPages`만 노출하고 Spring Data 타입(`Pageable`/`Sort` 등)이 API 응답에 새지 않게 함:

```java
@GetMapping
public ResponseEntity<GeneralResponse<PageResponse<ItemDto>>> list(Pageable pageable) {
    Page<ItemDto> page = itemService.findAll(pageable);
    return GeneralResponse.toResponseEntity(GeneralResponseCode.OK, PageResponse.from(page));
}
```

`GET /items?size=20` → size가 허용 목록에 없어서 10으로 대체됨. `GET /items?sort=name,asc` → `createdAt DESC` 대신 `name ASC` 적용.

## Feign

Feign 클라이언트를 쓰는 서비스라면 (`@FeignClient` 인터페이스 작성 + `@EnableFeignClients`는 여전히 서비스가 직접 해야 함 — 라이브러리가 대신 켜줄 수 없음) 아래가 자동으로 적용됨:

- **헤더 전파**: 원 요청의 `X-User-Id`/`X-User-Role` 헤더를 Feign으로 나가는 모든 요청에 그대로 실어줌. 서비스 간 호출 체인에서도 `RequestHeaderAuditorAware`(`createdBy`/`updatedBy`)가 계속 동작하게 해주는 용도. `@Async` 등 별도 스레드에서 Feign을 호출하면 전파 안 됨 (스레드 로컬 기반이라).
- **공통 에러 처리**: 호출한 서비스가 우리 `ErrorResponse` 포맷(`errorCode`, `message`)으로 에러를 내려주면, 그걸 파싱해서 `FeignApiException(errorCode, message, status)`을 던짐. `BusinessException`과 별도 타입이라 `GlobalExceptionHandler`가 도메인 예외와 구분해서 처리함 — 기본적으로는 `handleFeignApiException`이 잡아서 하위 서비스의 status/errorCode/message를 그대로 pass-through(표준 `HttpStatus`에 없는 코드면 502로 대체)하지만, 특정 에러를 자신의 도메인 에러로 바꾸고 싶은 서비스는 Feign 호출부에서 `FeignApiException`을 먼저 catch해서 원하는 대로 재변환하면 그 처리가 우선 적용됨. 우리 포맷이 아닌 응답(파싱 실패)이면 `ErrorResponseCode.FEIGN_CALL_FAILED`(`COMMON_9998`) `BusinessException`으로 대체됨.
- **로그 레벨**: `BASIC`(요청/응답 요약 + 소요시간)이 기본. 실제로 로그 찍으려면 서비스 쪽에 `logging.level.<FeignClient 패키지>: DEBUG`도 같이 필요함 (SLF4J 로거 자체가 DEBUG여야 Feign이 찍음).


## 필드 타입 컨벤션

- 시간: `Instant`
- 사용자 식별자(`createdBy` 등): `UUID` (`String`/`Long` 아님)

## Security

게이트웨이가 JWT 검증을 하고, 내부 서비스로는 `X-User-Id`(UUID) + `X-User-Role`(단일 role, 콤마 구분 아님) 헤더만 넘겨줌. `common`은 그 헤더를 믿고 파싱만 함 — 서비스가 JWT를 직접 검증하는 게 아님.

- `HeaderAuthenticationFilter` — 저 두 헤더를 읽어서 `Authentication`(`ROLE_<role>` 권한)을 만들고 `SecurityContextHolder`에 세팅. 실제 서명/JWT 검증을 하는 게 아니라, 이미 게이트웨이가 검증해서 내려준 결과를 SecurityContext에 반영만 하는 역할. 헤더가 없으면 그냥 익명 인증 상태로 넘어감(예외 안 던짐).
- `commonSecurityFilterChain` (기본 체인) — csrf/httpBasic/formLogin 비활성화, stateless 세션, 위 필터 등록까지 자동으로 해줌. `authorizeHttpRequests`는 URL 단위로 세밀하게 안 나누고 `anyRequest().permitAll()`로 둠 — "로그인 여부"는 게이트웨이가 이미 걸러주고, "누가 어떤 API를 쓸 수 있는가" 같은 세밀한 권한은 각 서비스 컨트롤러 메서드에 `@PreAuthorize`를 붙이는 방식으로 처리(권한 로직을 URL 매처와 `@PreAuthorize` 두 군데로 안 쪼개려는 의도).
- 서비스가 정말로 자기만의 `SecurityFilterChain`이 필요하면 직접 빈으로 정의하면 됨 — `@ConditionalOnMissingBean(SecurityFilterChain.class)`라서 서비스가 정의하는 순간 위 기본 체인은 자동으로 안 뜸. `HeaderAuthenticationFilter`는 별개 빈이라 그 경우에도 재사용 가능.

```text
@PreAuthorize("hasRole('ADMIN')")
@GetMapping("/admin/accounts/token-status")
public ResponseEntity<GeneralResponse<PageResponse<TokenStatusResponse>>> getTokenStatuses(Pageable pageable) {
    ...
}
```

### 서비스에서 `@PreAuthorize` 쓰려면

`HeaderAuthenticationFilter`가 `SecurityContext`를 채워주는 것과, `@PreAuthorize`가 실제로 동작하는 것은 별개임. 서비스마다 아래 두 가지를 직접 추가해야 함 — `common` 라이브러리 의존성만 추가한다고 자동으로 켜지지 않음:

1. **서비스 메인 클래스에 `@EnableMethodSecurity` 추가** — 서비스마다 독립된 Spring 컨텍스트(별도 JVM)라서, 이 스위치는 서비스별로 각자 켜야 함. `common`에서 대신 켜줄 수 없음.

```text
@SpringBootApplication
@EnableMethodSecurity
public class UserServiceApplication { ... }
```

2. **서비스 `build.gradle`에 `spring-security-config` 의존성 추가** — `common`의 `spring-boot-starter-security`는 `implementation`이라 컴파일 클래스패스로는 전이 안 되고 런타임에만 전이됨. `@EnableMethodSecurity`/`@PreAuthorize` 어노테이션 자체가 컴파일 시점에 필요하므로, 서비스가 직접 이 의존성을 가져야 함.

```text
implementation 'org.springframework.security:spring-security-config'
```

**주의**: 이 필터는 헤더를 서명 검증 없이 그대로 믿는 구조라, 서비스에 네트워크로 직접 접근 가능하면 헤더를 위조해서 우회할 수 있음. 실제 방어선은 (1) 게이트웨이의 JWT 검증, (2) 서비스가 외부에 노출되지 않는 네트워크 격리(`docker-compose.prod.yaml`에서 user/trading/market-service는 `ports` 없이 내부망에만 존재) — 이 필터 자체는 인증을 "검증"하는 게 아니라 이미 검증된 결과를 "전파"하는 역할.
