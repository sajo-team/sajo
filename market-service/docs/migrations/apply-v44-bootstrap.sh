#!/usr/bin/env bash
# market-service 최초 DB 부트스트랩 시 V44만 안전하게 실행하는 도우미.
#
# 실행 위치: EC2 서버 안, 아무 위치에서나 실행 가능 (경로는 알아서 계산함)
# 전제: docker compose로 postgres, market-service가 이미 떠있는 상태
#       (market-service를 SPRING_JPA_HIBERNATE_DDL_AUTO=update로 한 번 부팅해서
#        테이블이 이미 생성된 뒤)
#
# 사용법: 그냥 이 파일 실행하면 끝. 환경변수 설정 필요 없음.
#   ./apply-v44-bootstrap.sh
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
MIGRATION_FILE="$REPO_ROOT/market-service/src/main/resources/db/migration/V44__market_stock_price_daily_unique.sql"
COMPOSE_FILE="$REPO_ROOT/docker-compose.prod.yaml"

cd "$REPO_ROOT"

if [ ! -f "$MIGRATION_FILE" ]; then
  echo "❌ 마이그레이션 파일을 못 찾았습니다: $MIGRATION_FILE"
  exit 1
fi

DB_EXEC() {
  docker compose -f "$COMPOSE_FILE" exec -T postgres psql -U postgres -d sajo "$@"
}

echo "[0/3] postgres 컨테이너 확인..."
if ! docker compose -f "$COMPOSE_FILE" exec -T postgres pg_isready -U postgres > /dev/null 2>&1; then
  echo "❌ postgres 컨테이너가 안 떠있거나 준비되지 않았습니다."
  echo "   'docker compose -f docker-compose.prod.yaml ps'로 상태를 먼저 확인하세요."
  exit 1
fi
echo "✅ postgres 정상."

echo "[1/3] market_strategy.m_market_stocks_price 테이블 존재 확인..."
TABLE_EXISTS=$(DB_EXEC -tAc "
  SELECT EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema = 'market_strategy' AND table_name = 'm_market_stocks_price'
  );
")

if [ "$TABLE_EXISTS" != "t" ]; then
  echo "❌ 테이블이 아직 없습니다."
  echo "   market-service가 SPRING_JPA_HIBERNATE_DDL_AUTO=update로 정상 기동됐는지 먼저 확인하세요."
  exit 1
fi
echo "✅ 테이블 존재 확인됨."

echo "[2/3] V44 마이그레이션 실행..."
DB_EXEC -v ON_ERROR_STOP=1 < "$MIGRATION_FILE"
echo "✅ V44 실행 완료."

echo "[3/3] partial unique index 생성 결과 확인..."
INDEX_EXISTS=$(DB_EXEC -tAc "
  SELECT EXISTS (
    SELECT 1 FROM pg_indexes
    WHERE schemaname = 'market_strategy'
      AND tablename = 'm_market_stocks_price'
      AND indexname = 'uk_market_stock_price_daily_rest'
  );
")

if [ "$INDEX_EXISTS" != "t" ]; then
  echo "❌ 인덱스가 생성되지 않았습니다. 수동으로 확인이 필요합니다."
  exit 1
fi

echo "✅ 완료: uk_market_stock_price_daily_rest 인덱스 생성 확인됨."
echo ""
echo "다음 단계: docker-compose.prod.yaml에서 SPRING_JPA_HIBERNATE_DDL_AUTO: update"
echo "두 줄(market-service, trading-service)을 삭제하는 커밋을 올리고 재배포하세요."
