# SAJO Project Guidelines

## 1. Project Overview

SAJO는 국내 주식 자동매매 및 AI 위험 분석 기능을 제공하는 MSA 기반 프로젝트이다.

Claude Code는 Pull Request를 리뷰할 때 이 문서의 규칙을 프로젝트 전용 리뷰 기준으로 사용한다.

단순한 코드 스타일이나 개인 취향에 대한 지적보다 실제 버그, 장애, 데이터 정합성, 보안, 동시성, 성능 및 아키텍처 문제를 우선한다.


## 2. Architecture

### Layered Architecture

기본적인 의존 방향은 다음과 같다.

Controller → Service → Repository

- Controller는 HTTP 요청/응답과 입력 검증을 담당한다.
- Service는 비즈니스 로직과 유스케이스를 담당한다.
- Repository는 데이터 영속성을 담당한다.
- Controller에 핵심 비즈니스 로직을 작성하지 않는다.
- Repository에서 Controller 계층을 직접 참조하지 않는다.


## 3. Command / Query Separation

Command와 Query의 책임을 분리한다.

- 생성, 수정, 삭제 등 상태를 변경하는 로직은 `command` 패키지에 위치시킨다.
- 조회 로직은 `query` 패키지에 위치시킨다.
- 하나의 Service에서 조회와 상태 변경 책임을 혼합하지 않는다.
- PR 리뷰 시 기존 Command / Query 책임 분리를 깨뜨리는 변경이 있는지 확인한다.


## 4. API

### Request / Response DTO

- API 요청 객체는 `controller/dto/request`에 위치시킨다.
- API 응답 객체는 `controller/dto/response`에 위치시킨다.
- Request DTO의 입력값 검증에는 Jakarta Validation을 사용한다.
- 검증이 필요한 Request Body에는 `@Valid`를 적용한다.

### Common Response

API 성공 응답은 공통 라이브러리의 `GeneralResponse<T>`와 `GeneralResponseCode`를 사용한다.

도메인마다 별도의 성공 응답 wrapper를 임의로 만들지 않는다.


## 5. Exception Handling

- 비즈니스 예외는 공통 `BusinessException`을 사용한다.
- 도메인별 오류는 해당 도메인의 `*ErrorCode`로 정의한다.
- 도메인 ErrorCode는 공통 `ErrorCode` 규약을 따른다.
- Controller에서 비즈니스 예외를 직접 HTTP 응답으로 변환하지 않는다.
- 공통 예외 응답 처리는 `GlobalExceptionHandler`의 기존 정책을 따른다.


## 6. Entity and Auditing

공통 라이브러리에서 제공하는 Entity 기반 클래스를 사용한다.

### BaseEntity

생성 이후 수정되지 않는 이력성 데이터에 사용한다.

예:
- 감사 로그
- 시세 이력
- 스냅샷 데이터

### BaseUpdatableEntity

수정 또는 Soft Delete가 필요한 일반적인 도메인 Entity에 사용한다.

- 공통 Auditing 필드를 도메인 Entity에서 중복 선언하지 않는다.
- Soft Delete 대상 Entity는 기존 공통 삭제 정책을 우선 사용한다.


## 7. Transaction

- 데이터 상태를 변경하는 Command 작업은 적절한 Transaction 경계 안에서 처리한다.
- Transaction 범위를 불필요하게 넓히지 않는다.
- 외부 API 및 다른 서비스 호출이 Transaction 내부에 포함될 경우 장시간 Transaction이나 데이터 정합성 문제가 발생하지 않는지 확인한다.

> Query Service의 `@Transactional(readOnly = true)` 적용 여부는 아직 프로젝트 공통 규칙으로 강제하지 않는다.


## 8. MSA Communication

- 각 서비스는 자신의 도메인과 데이터에 대한 책임을 가진다.
- 다른 서비스의 Repository 또는 DB에 직접 접근하지 않는다.
- 서비스 간 동기 통신이 필요한 경우 프로젝트의 Feign 통신 구조를 따른다.
- 내부 서비스 API는 프로젝트에서 사용하는 `/internal/v1/**` 규칙을 따른다.
- Feign Client는 호출하는 서비스에서 정의한다.
- Feign 오류 처리는 공통 Feign 예외 처리 정책을 우선 사용한다.
- 서비스 간 결합도를 불필요하게 증가시키는 구현을 피한다.


## 9. Review Priority

Claude Code는 다음 순서로 문제를 우선 검토한다.

1. Critical
    - 인증/인가 우회
    - 민감 정보 노출
    - 잘못된 주문 또는 중복 주문 가능성
    - 심각한 데이터 손상 또는 데이터 정합성 문제

2. High
    - 잘못된 비즈니스 로직
    - Transaction 경계 문제
    - Race Condition 및 동시성 문제
    - 예외 처리 누락
    - 서비스 책임 침범

3. Medium
    - N+1 Query
    - 불필요하거나 비효율적인 DB 접근
    - Layered Architecture 위반
    - Command / Query 책임 혼합
    - 주요 Edge Case 처리 누락
    - 주요 로직의 테스트 누락

4. Low
    - 단순 코드 스타일
    - 개인 취향에 따른 네이밍
    - 동작에 영향을 주지 않는 사소한 리팩토링

Low 수준의 문제만 존재하는 경우 불필요한 리뷰 댓글을 남기지 않는다.


## 10. Review Guidelines

PR 리뷰 시 변경된 코드와 해당 변경을 이해하는 데 필요한 주변 코드만 검토한다.

각 중요한 문제에 대해 다음을 설명한다.

- 어떤 문제가 있는지
- 왜 문제가 발생할 수 있는지
- 어떤 상황에서 문제가 발생하는지
- 구체적으로 어떻게 수정할 수 있는지

근거가 불충분한 문제를 확정적인 버그로 표현하지 않는다.

프로젝트에서 아직 합의되지 않은 규칙을 새로운 팀 규칙으로 가정하지 않는다.