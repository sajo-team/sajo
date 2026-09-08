# 최초 운영 배포 (dev → main) 런북

dev → main 최초 이관 및 최초 프로덕션 배포를 위한 체크리스트입니다.
`deploy.yaml`은 main에 대한 CI Build 성공 즉시 EC2 자동 배포까지 트리거하므로,
**main에 push하는 순간 = 운영 배포 시작**입니다. 아래 항목은 그 전에 끝나 있어야 합니다.

## 0. 배포 전 팀 확인 (코드 변경 없음, 사람이 확인/합의)

- [ ] `sajo-config-repo`에 gateway/user-service의 `sajo.jwt.secret` 운영 값이 등록되어 있고, 두 서비스가 **동일한 값**을 참조하는지 확인 (코드에 TODO로 남아있던 항목)
- [ ] GitHub Actions repo secrets에 `EC2_HOST`, `EC2_USER`, `EC2_SSH_KEY`가 등록되어 있는지 확인 (최초 배포라 미검증 상태일 수 있음)
- [ ] EC2에 `~/sajo`가 미리 clone되어 있고, docker/docker compose가 설치되어 있으며, `.env` 파일이 채워져 있는지 확인
  (`DB_PASSWORD, MONGO_PASSWORD, REDIS_PASSWORD, GRAFANA_ADMIN_USER/PASSWORD, GITHUB_USERNAME/TOKEN, ENCRYPT_KEY, ACCOUNT_ENCRYPTION_KEY/SALT, ACCOUNT_HASH_KEY, OPENAI_API_KEY, JWT_SECRET`)
- [ ] `config-server`가 쓰는 `GITHUB_TOKEN`이 `sajo-config-repo`에 대한 읽기 권한이 있고 만료되지 않았는지 확인
- [ ] **KIS 엔드포인트**: 내일 오픈이 모의투자인지 실거래인지, `trading-service`의 `KIS_BASE_URL`이 그에 맞게 설정될 예정인지 트레이딩 담당자와 재확인 (계좌는 REAL/VIRTUAL 두 종류인데 주문 실행 엔드포인트는 서비스 단일 설정값)
- [ ] gateway가 `8080:8080`으로 퍼블릭 노출되는데, HTTPS 종단(Nginx/ALB 등)이 이 레포 밖에서 별도로 준비되어 있는지 확인

## 1. DB 최초 부트스트랩 (market-service, trading-service)

운영 DB는 스키마(네임스페이스)만 있고 테이블이 전혀 없는 상태이며, 두 서비스 모두
prod 프로필이 `ddl-auto: validate`라 그대로 두면 기동 자체가 실패합니다.
(자세한 근거와 실제 검증 결과는 `market-service/docs/migrations/README.md`의
"Bootstrapping a brand-new (empty) production database" 절 참고)

1. [ ] sajo-config-repo에서 market-service, trading-service의 prod `ddl-auto`를
       **일시적으로** `update`로 변경
2. [ ] 두 서비스를 정상 기동 → 헬스체크 UP 확인
3. [ ] market-service 컨테이너/호스트에서 `V44__market_stock_price_daily_unique.sql`
       **만** 수동 실행 (V52/V53/V103은 실행하지 않음 — 불필요하거나 에러 발생)
4. [ ] sajo-config-repo에서 두 서비스 prod `ddl-auto`를 다시 `validate`로 되돌리고 재기동
5. [ ] 재기동 후에도 헬스체크 UP 유지되는지 확인 (validate가 실제로 통과하는지 확인하는 단계)

## 2. main으로 이관 및 배포 트리거

1. [ ] 위 0, 1번이 모두 끝난 뒤에 dev → main 머지/푸시 진행
2. [ ] main 푸시 즉시 "CI Build" 워크플로우가 돌고, 성공 시 "CD Deploy"가 자동으로 이어짐 (수동 승인 단계 없음)
3. [ ] **최초 배포라 자동 롤백이 동작하지 않습니다** (`~/sajo/.last_healthy_sha`가 아직 없어서
       헬스체크 실패 시 되돌아갈 이전 정상 커밋이 없음). 배포가 진행되는 동안 팀이 대기하며
       실패 시 수동 개입할 준비를 해둘 것.

## 3. 배포 후 확인

- [ ] `docker compose -f docker-compose.prod.yaml ps`로 모든 컨테이너 healthy 확인
- [ ] gateway를 통해 각 서비스 API 실제 호출 테스트 (로그인 → JWT 발급 → 인증 필요 API 순으로,
      gateway/user-service JWT_SECRET 불일치 여부가 여기서 바로 드러남)
- [ ] Prometheus/Grafana 대시보드에서 5개 서비스 메트릭 수집 확인
- [ ] `~/sajo/.last_healthy_sha`가 이번 배포 커밋 SHA로 기록됐는지 확인 (다음 배포부터 롤백 안전망이 생기는 시점)
