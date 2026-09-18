# Synthetic Data 기반 부하 테스트 (#263 후속)

`quote-baseline.jmx`(스레드 100개가 1초 안에 5개 종목에 균등하게 몰리는 인위적인 시나리오)와 달리,
실제 트레이딩 앱 사용 패턴에 가깝게 통계적으로 편향을 준 Synthetic Data로 다시 부하를 재현한다.

## 1. 시나리오 설계

`generate_synthetic_traffic.py`가 아래 세 가지 편향을 반영해 요청 이벤트 1,000건을 생성한다.

| 편향 | 근거 | 반영 방법 |
| --- | --- | --- |
| 종목 인기도 | 실제로는 시가총액 상위 종목(삼성전자 등)에 조회가 몰리고 롱테일 종목은 적게 조회됨 | 5개 종목에 Zipf 분포(exponent=0.85) 적용 |
| 사용자 활동성 | 소수의 활발한 사용자가 조회 요청의 더 큰 비중을 차지함 | 가상 사용자 400명에 Zipf 분포(exponent=0.65) 적용 |
| 시간대별 접속 | 장 시작 직후(09:00~09:20)와 마감 직전(15:10~15:30)에 조회가 집중되고, 점심 이후 완만하게 재개되는 U자형 패턴 | 정규분포 3개를 섞은 혼합분포로 60초 테스트 윈도우에 압축 재현(개장 45% / 점심 이후 15% / 마감 40%) |

실행 시 매번 지수(exponent)를 어떻게 골랐는지는 스크립트 상단 docstring에 남겨뒀다 — 처음에는
더 높은 지수(1.1~1.3)로 시도했으나 특정 종목/사용자 1명이 전체 요청의 15~50%를 차지하는 등
현실보다 과장된 결과가 나와, 실측 KOSPI 대형주 거래 비중과 비슷한 수준(1위 종목 40%대)이 되도록
지수를 낮췄다.

## 1-1. X-User-Id 처리에 대한 주의

`MarketQuoteQueryService.fetchAndCacheQuote()`는 캐시 미스(=대표 스레드)일 때 요청 헤더의
`X-User-Id`로 user-service에서 KIS 계좌 정보를 조회한다(`UserAccountFeignClient.getKisToken`).
실제로 등록되지 않은 계좌면 "계좌를 찾을 수 없습니다" 에러가 나므로, 가상 사용자 400명 각각에게
새 UUID를 부여하는 최초 버전은 100% 에러로 실패했다(실제로 겪은 문제).

현재가 캐시는 `stockCode`로만 키가 잡히고 사용자별로 나뉘지 않으므로, 부하 테스트의 목적
(Single-flight/락 경합 재현) 자체에는 요청마다 실제 계좌가 다 있을 필요가 없다. 그래서 실제
X-User-Id 헤더 값은 `quote-baseline.jmx`와 동일한 실제 테스트 계정
(`e7bfaf56-5ff0-4bb6-a6fe-14575ef18e5e`)으로 통일하고, "가상 사용자별 활동성 편향"은
`syntheticUserLabel` 컬럼에만 별도로 남겨 통계/리포트용으로 쓴다.

## 2. 생성 및 재현성

```bash
cd loadtest/synthetic
python3 generate_synthetic_traffic.py
```

`RNG_SEED = 20260918`로 고정돼 있어 항상 같은 `synthetic_quote_traffic.csv`가 생성된다(재현성 확보).
실행하면 종목별 비중 / 활동성 상위 사용자 / 10초 구간별 요청 분포를 콘솔에 요약 출력한다 — 이 값을
보고서 2-1/2-2에 그대로 인용한다.

## 3. JMeter 테스트 계획 (`quote-synthetic.jmx`)

CSV를 그대로 하나의 Thread Group에 넣고 ramp-up만 60초로 늘리면, JMeter는 스레드를 균일한
속도로만 시작시키기 때문에 "개장 직후·마감 직전에 몰린다"는 시간대별 쏠림 자체가 사라져 버린다
(도착 순서는 유지되지만 몰림의 강도가 평평해짐). 이를 최대한 재현하기 위해, CSV를 도착 시각
순으로 정렬해 둔 뒤 Thread Group을 3단계로 나눠 각 구간의 몰림 강도를 근사했다.

| Thread Group | 구간 | 스레드 수 | Ramp-up | Startup Delay |
| --- | --- | --- | --- | --- |
| Open Rush | 0~10s | 424 | 10s | 0s |
| Midday | 10~50s | 171 | 40s | 10s |
| Close Rush | 50~60s | 405 | 10s | 50s |

세 Thread Group은 파일 상단에 둔 CSV Data Set Config 하나(`shareMode.all`)를 공유해서, 앞 구간
Thread Group이 먼저 소비한 만큼 다음 Thread Group이 이어서 읽는다 — CSV가 이미 도착 시각순으로
정렬돼 있으므로 구간별 스레드 수·시작 시각을 맞추면 시간대별 요청 수가 실제 분포와 근사하게
맞아떨어진다.

**한계**: 각 구간 내부에서는 여전히 스레드가 균일한 속도로 시작한다(예: Open Rush 구간 안에서
정규분포처럼 더 촘촘하게 몰리는 부분까지는 재현하지 못하고, 10초 동안 평평하게 424개가 시작됨).
플러그인(Concurrency Thread Group 등) 없이 코어 JMeter 기능만으로 재현할 수 있는 근사치다.

## 4. 실행

```bash
cd loadtest
jmeter -n -t quote-synthetic.jmx -l results/quote-synthetic-result.jtl -e -o results/synthetic-report
```

실행 전 `quote-baseline.jmx`와 마찬가지로 market-service가 `http://localhost:8082`에서 응답하는지
먼저 확인한다.

## 5. 베이스라인/개선 결과와의 비교 포인트

- 총 요청 수(1,000건)는 기존 베이스라인과 동일하게 맞춰 처리량·지연 시간을 직접 비교할 수 있게 했다.
- 다만 이번에는 요청이 5개 종목에 균등하게(각 20%) 분산되지 않고 삼성전자에 40%가 몰리므로,
  Single-flight 병합 효과가 더 크게 나타날 가능성이 높다 — 같은 종목에 몰릴수록 대표 스레드가
  대신 처리해주는 요청 비율이 늘어나기 때문이다. 실제로 그렇게 나오는지 결과에서 확인한다.
