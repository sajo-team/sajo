# 현재가 조회 API 부하 테스트 (#259)

`GET /api/v1/market/quote` 베이스라인(개선 전) 부하 테스트 자료.

## 파일 구성

- `quote-baseline.jmx` — JMeter 테스트 계획. Thread Group: Threads 100 / Ramp-up 1 / Loop Count 10 (총 1,000 요청).
- `stock_codes.csv` — 조회 대상 종목코드(삼성전자, SK하이닉스 등 대형주). 각 스레드가 순회하며 사용.
- `results/` — 실행 결과(`.jtl`)가 저장되는 폴더. `.gitignore`에 추가 검토 필요(용량이 커질 수 있음).

## 실행 전 준비

1. `docker compose up -d`로 인프라(postgres, redis, kafka 등) + market-service를 기동한다.
   (또는 IDE에서 market-service만 로컬로 직접 실행해도 된다 — 이 경우 `.jmx`의 `BASE_PORT`를
   실제 실행 포트(8082)로 맞춘다.)
2. market-service가 `http://localhost:8082/api/v1/market/quote`로 응답하는지 curl로 먼저 확인한다.

```bash
curl -H "X-User-Id: $(uuidgen)" "http://localhost:8082/api/v1/market/quote?stockCode=005930"
```

## 왜 Gateway가 아니라 market-service에 직접 요청하는가

Gateway(`/api/v1/market/**` → `lb://market-service`)를 통해서도 호출은 가능하지만, Gateway 앞단에는
JWT 인증이 있어 부하 테스트용 토큰 발급이 별도로 필요하고, Gateway 자체의 라우팅 오버헤드가
market-service 자체의 처리량 측정에 섞여 들어간다. 이번 베이스라인의 목적은 "market-service의
현재가 조회 경로 자체"의 한계를 측정하는 것이므로, market-service에 직접 요청한다
(`X-User-Id`는 내부 신뢰 헤더로 직접 주입). 전체 스택(Gateway 포함) 처리량이 필요해지면 별도
시나리오로 추가한다.

## JMeter 실행

GUI 없이 커맨드라인으로 실행(권장 — 부하 테스트 중 GUI 자체가 리소스를 먹는 것을 방지):

```bash
cd loadtest
jmeter -n -t quote-baseline.jmx -l results/quote-baseline-result.jtl -e -o results/report
```

- `-n`: non-GUI 모드
- `-l`: 결과를 저장할 `.jtl` 파일
- `-e -o`: 실행 후 HTML 대시보드 리포트 생성(`results/report/index.html`)

GUI로 열어 Summary Report를 직접 보고 싶다면 `jmeter -t quote-baseline.jmx`로 연다.

## 측정 환경 기록 (필수 — 실행할 때마다 채워서 결과와 함께 남길 것)

| 항목 | 값 |
|---|---|
| 실행 환경 | (로컬 / Docker / 클라우드) |
| 머신 | (예: MacBook Air, Apple Silicon) |
| CPU 코어 수 | |
| 메모리 | |
| market-service 인스턴스 수 | |
| DB(postgres) 구동 위치 | (같은 머신 / 별도 머신) |
| Kafka 구동 위치 | (같은 머신 / 별도 머신) |
| Redis 구동 위치 | (같은 머신 / 별도 머신) |
| 테스트 일시 | |

## 결과 기록 (베이스라인)

| 항목 | 값 |
|---|---|
| Throughput (처리량, req/sec) | |
| Average (평균 응답시간, ms) | |
| Min / Max (ms) | |
| Error % | |

이 값을 이후 개선(캐싱 튜닝, Kafka 비동기화 효과 재확인, 커넥션 풀 조정 등) 작업의 비교 기준으로 사용한다.

## 부하 중 함께 확인할 것

- Kafka-UI(`http://localhost:8090`)에서 `market.quote.requested` 토픽에 요청 수만큼 메시지가
  쌓이는지, Consumer Lag이 과도하게 쌓이지 않는지 확인한다(#248).
- market-service 로그에 `MarketQuoteRequestedEvent 발행 큐가 가득 차...` 경고가 찍히는지
  확인한다 — 찍힌다면 발행 큐 용량(현재 10,000)이 이번 부하 수준에는 부족하다는 신호다.
