"""
현재가 조회 API(GET /api/v1/market/quote) 부하 테스트용 Synthetic Data 생성 스크립트(#263 후속 과제).

기존 베이스라인(quote-baseline.jmx)은 "스레드 100개가 1초 만에 5개 종목에 균등하게 몰린다"는
인위적인 시나리오였다. 이번 Synthetic Data는 실제 트레이딩 앱 사용 패턴에 가깝게 아래 세 가지
편향(skew)을 통계적으로 반영해서 생성한다.

1. 종목 인기도 편향 — 실제로는 시가총액 상위 종목(삼성전자 등)에 조회가 몰리고 롱테일 종목은
   상대적으로 적게 조회된다. Zipf 분포(exponent=0.85)로 흉내낸다.
2. 사용자 활동성 편향 — 소수의 "활발한" 사용자가 조회 요청의 큰 비중을 차지하고, 대다수는
   어쩌다 한 번 조회한다. Zipf 분포(exponent=0.65)로 흉내낸다.
3. 시간대별 접속 편향 — 실제 장중에는 개장 직후(09:00~09:20)와 장 마감 직전(15:10~15:30)에
   조회가 집중되고, 점심시간 전후로는 완만하게 재개되는 U자형 패턴이 나타난다. 이를 정규분포
   3개를 섞은 혼합분포로 근사해, 60초 테스트 윈도우 안에 압축해서 재현한다(09:00~15:30을
   0~60초로 매핑).

이 세 분포를 조합해 이벤트(요청) 1,000건을 샘플링하고, 도착 시각(arrivalOffsetMs) 순으로
정렬해 CSV로 저장한다. JMeter 쪽에서는 이 순서 그대로 스레드가 순차적으로 CSV 행을 읽어가며
ramp-up 동안 균등하게 시작하므로, "이른 시각에 몰린 행일수록 먼저 실행"되는 방식으로 시간대별
쏠림을 근사한다(스레드 시작 간격이 완전히 균일하다는 한계는 README에 명시).
"""

import csv
import uuid
import numpy as np

RNG_SEED = 20260918  # 재현 가능하도록 고정

# market-service의 fetchAndCacheQuote()는 캐시 미스(=대표 스레드)일 때 이 X-User-Id로
# user-service에 KIS 계좌 정보를 조회한다(UserAccountFeignClient.getKisToken). 실제로 등록된
# 계좌가 아니면 "계좌를 찾을 수 없습니다" 에러가 나므로, quote-baseline.jmx가 쓰던 것과 같은
# 실제 테스트 계정 UUID를 그대로 재사용한다. 현재가 캐시는 stockCode로만 키가 잡히고
# 사용자별로 나뉘지 않으므로(#248/#259), 부하 테스트의 목적(Single-flight/락 경합 재현)
# 자체에는 사용자별 실제 계좌가 굳이 필요 없다 — 사용자 활동성 편향은 별도 라벨
# (syntheticUserLabel)로만 기록해 통계/리포트에 쓰고, 실제 X-User-Id 헤더 값에는 영향을 주지
# 않는다.
BASELINE_ACCOUNT_USER_ID = "e7bfaf56-5ff0-4bb6-a6fe-14575ef18e5e"
NUM_EVENTS = 1000  # 기존 베이스라인과 동일한 총 요청 수로 맞춰 비교 가능하게 함
NUM_VIRTUAL_USERS = 400
TEST_WINDOW_SECONDS = 60  # 09:00~15:30(장중 6시간30분)을 60초로 압축

STOCKS = [
    ("005930", "삼성전자"),
    ("000660", "SK하이닉스"),
    ("035420", "NAVER"),
    ("035720", "카카오"),
    ("051910", "LG화학"),
]


def zipf_weights(n: int, exponent: float) -> np.ndarray:
    ranks = np.arange(1, n + 1)
    weights = 1.0 / np.power(ranks, exponent)
    return weights / weights.sum()


def sample_arrival_offsets_ms(rng: np.random.Generator, count: int) -> np.ndarray:
    """개장 직후 + 마감 직전 급증, 점심 이후 완만한 재개를 섞은 혼합분포에서 샘플링한다."""
    component = rng.choice(
        ["open_rush", "midday", "close_rush"],
        size=count,
        p=[0.45, 0.15, 0.40],
    )
    offsets = np.empty(count, dtype=float)

    open_mask = component == "open_rush"
    offsets[open_mask] = rng.normal(loc=5.0, scale=3.0, size=open_mask.sum())

    midday_mask = component == "midday"
    offsets[midday_mask] = rng.normal(loc=30.0, scale=8.0, size=midday_mask.sum())

    close_mask = component == "close_rush"
    offsets[close_mask] = rng.normal(loc=55.0, scale=3.0, size=close_mask.sum())

    offsets = np.clip(offsets, 0.0, float(TEST_WINDOW_SECONDS))
    return offsets * 1000.0  # ms


def main() -> None:
    rng = np.random.default_rng(RNG_SEED)

    # "가상 사용자"는 실제 계좌가 아니라 활동성 편향(Zipf)을 부여하기 위한 라벨일 뿐이다.
    # 실제 X-User-Id 헤더 값은 전부 BASELINE_ACCOUNT_USER_ID로 통일한다(위 설명 참고).
    user_labels = [f"synthetic-user-{i:04d}" for i in range(NUM_VIRTUAL_USERS)]

    user_weights = zipf_weights(NUM_VIRTUAL_USERS, exponent=0.65)
    stock_weights = zipf_weights(len(STOCKS), exponent=0.85)

    user_indices = rng.choice(NUM_VIRTUAL_USERS, size=NUM_EVENTS, p=user_weights)
    stock_indices = rng.choice(len(STOCKS), size=NUM_EVENTS, p=stock_weights)
    arrival_offsets_ms = sample_arrival_offsets_ms(rng, NUM_EVENTS)

    rows = []
    for i in range(NUM_EVENTS):
        user_label = user_labels[user_indices[i]]
        stock_code, _ = STOCKS[stock_indices[i]]
        rows.append((arrival_offsets_ms[i], BASELINE_ACCOUNT_USER_ID, stock_code, user_label))

    rows.sort(key=lambda r: r[0])

    out_path = "synthetic_quote_traffic.csv"
    with open(out_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["userId", "stockCode", "arrivalOffsetMs", "syntheticUserLabel"])
        for offset_ms, user_id, stock_code, user_label in rows:
            writer.writerow([user_id, stock_code, f"{offset_ms:.0f}", user_label])

    # 리포트 2-1/2-2에 쓸 요약 통계 출력
    print(f"총 이벤트 수: {NUM_EVENTS}, 가상 사용자 수: {NUM_VIRTUAL_USERS}")
    print("\n[종목별 조회 비중]")
    stock_codes_arr = np.array([STOCKS[i][0] for i in stock_indices])
    for code, name in STOCKS:
        share = (stock_codes_arr == code).mean() * 100
        print(f"  {code}({name}): {share:.1f}%")

    print("\n[사용자 활동성 상위 5명 요청 비중]")
    user_labels_arr = np.array([user_labels[i] for i in user_indices])
    unique, counts = np.unique(user_labels_arr, return_counts=True)
    top5 = sorted(zip(unique, counts), key=lambda x: -x[1])[:5]
    for label, cnt in top5:
        print(f"  {label}: {cnt}건 ({cnt / NUM_EVENTS * 100:.1f}%)")

    print("\n[시간대별(10초 구간) 요청 분포]")
    bins = np.arange(0, TEST_WINDOW_SECONDS + 10, 10)
    hist, _ = np.histogram(arrival_offsets_ms / 1000.0, bins=bins)
    for i, count in enumerate(hist):
        print(f"  {bins[i]:>2}s~{bins[i+1]:>2}s: {'#' * (count // 5)} ({count}건)")


if __name__ == "__main__":
    main()
