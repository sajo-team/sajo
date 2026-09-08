#!/usr/bin/env bash
# market-service 최초 DB 부트스트랩 시 V44만 안전하게 실행하는 도우미 스크립트.
#
# 전제: market-service를 ddl-auto=update로 한 번 부팅해서 테이블이 이미 생성된 뒤에 실행.
# (docs/README.md "Bootstrapping a brand-new (empty) production database" 참고)
#
# 사용법 (EC2에서, postgres 컨테이너에 접근 가능한 곳에서):
#   PGHOST=localhost PGPORT=5432 PGUSER=postgres PGPASSWORD=*** PGDATABASE=sajo \
#     ./apply-v44-bootstrap.sh
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MIGRATION_FILE="$SCRIPT_DIR/../../src/main/resources/db/migration/V44__market_stock_price_daily_unique.sql"

: "${PGHOST:?PGHOST 환경변수를 설정하세요}"
: "${PGUSER:?PGUSER 환경변수를 설정하세요}"
: "${PGDATABASE:?PGDATABASE 환경변수를 설정하세요}"

echo "[1/3] market_strategy.m_market_stocks_price 테이블 존재 확인..."
TABLE_EXISTS=$(psql -tAc "
  SELECT EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema = 'market_strategy' AND table_name = 'm_market_stocks_price'
  );
")

if [ "$TABLE_EXISTS" != "t" ]; then
  echo "❌ 테이블이 아직 없습니다."
  echo "   market-service를 ddl-auto=update로 먼저 정상 기동시킨 뒤 다시 실행하세요."
  exit 1
fi
echo "✅ 테이블 존재 확인됨."

echo "[2/3] V44 마이그레이션 실행..."
psql -v ON_ERROR_STOP=1 -f "$MIGRATION_FILE"
echo "✅ V44 실행 완료."

echo "[3/3] partial unique index 생성 결과 확인..."
INDEX_EXISTS=$(psql -tAc "
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
echo "다음 단계: sajo-config-repo에서 market-service, trading-service의"
echo "ddl-auto를 다시 'validate'로 되돌리고 재기동하세요."
