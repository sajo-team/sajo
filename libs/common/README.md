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
    PARTIALLY_UPDATED(HttpStatus.OK, "일부 필드만 업데이트되었습니다");
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
    ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "ITEM_0001", "아이템을 찾을 수 없습니다");
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

## 필드 타입 컨벤션

- 시간: `Instant`
- 사용자 식별자(`createdBy` 등): `UUID` (`String`/`Long` 아님)

## Security
추후 인증 구현되면 구현 예정
