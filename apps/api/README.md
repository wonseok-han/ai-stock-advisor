# apps/api — 지금이니?! Backend

Spring Boot 3.5.13 / Java 21 / Gradle (Kotlin DSL). 가상 스레드 + WebFlux(외부 API 전용) 혼합.

> 루트 개요: [`../../README.md`](../../README.md) · 프로젝트 규칙: [`../../CLAUDE.md`](../../CLAUDE.md)

---

## 요구 사항

- JDK 21 (Toolchain 자동 프로비저닝도 가능하지만 로컬 21 권장)
- Docker (Testcontainers + 로컬 Postgres / Redis)
- 외부 API 키 (최소 Finnhub + Gemini; Supabase 서비스 롤 키)

## 빠른 시작

```bash
cd apps/api
# 로컬 인프라 (루트 Makefile)
cd ../.. && make infra-up && cd apps/api

# 환경 변수 — application.yml 의 ${VAR:default} 는 .env.local 로 주입
# (자세한 키 목록은 아래 '환경 변수' 섹션 참조)
cat > .env.local <<'EOF'
SUPABASE_URL=https://<ref>.supabase.co
SUPABASE_SERVICE_ROLE_KEY=<service-role-key>
FINNHUB_API_KEY=<key>
GEMINI_API_KEY=<key>
VAPID_PUBLIC_KEY=<key>
VAPID_PRIVATE_KEY=<key>
EOF

# 개발 서버 (:8080)
./gradlew bootRun
# 또는 루트에서
cd ../.. && make api-dev
```

Makefile 의 `api-dev` 타깃이 `.env.local` 을 자동 source 합니다.

## Gradle 태스크

| 태스크 | 설명 |
|---|---|
| `./gradlew bootRun` | 개발 서버 |
| `./gradlew build` | 테스트 + 빌드 (jar) |
| `./gradlew build -x test` | 테스트 스킵 빌드 |
| `./gradlew test` | 테스트만 (Testcontainers 필요) |
| `./gradlew check` | 테스트 + 정적 분석 (CI 와 동등) |
| `./gradlew clean` | 빌드 산출물 삭제 |

루트 Makefile: `make api-dev`, `make api-build`, `make api-check`, `make api-test`, `make api-clean`.

## 환경 변수

`application.yml` 의 `${VAR:default}` 자리표시자 기준. 로컬은 `.env.local` 에 작성 (Git 무시).

| 그룹 | 키 | 용도 |
|---|---|---|
| DB | `DATABASE_URL` / `DATABASE_USERNAME` / `DATABASE_PASSWORD` | PostgreSQL (Supabase Pooler 또는 로컬) |
| Cache | `REDIS_URL` / `REDIS_PASSWORD` | Redis (Upstash 또는 로컬) |
| Supabase | `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY` | Auth JWT 검증 + service-role API |
| Gemini | `GEMINI_API_KEY`, `GEMINI_MODEL` (기본 `gemini-2.5-flash`), `GEMINI_TIMEOUT_MS` | LLM |
| 시세 API | `FINNHUB_API_KEY`, `TWELVE_DATA_API_KEY`, `FMP_API_KEY`, `ALPHAVANTAGE_API_KEY` | 시세·뉴스·펀더멘털 |
| CORS | `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` 외에 프로덕션 FE origin 추가 |
| 웹 푸시 | `VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY`, `VAPID_SUBJECT` | Web Push (mailto 포함) |
| 이메일 | `RESEND_API_KEY`, `CONTACT_EMAIL` | 피드백 이메일 알림 (Resend) |
| 알림 튜닝 | `NOTIFICATION_DEDUP_RESET_RATIO`, `NOTIFICATION_DEDUP_COOLDOWN` | 히스테리시스 + 쿨다운 |
| 캐시 TTL | `NEWS_CACHE_TTL_HOURS`, `AI_SIGNAL_CACHE_TTL_MINUTES` | Redis TTL |
| 레이트 리밋 | `AI_SIGNAL_RATE_LIMIT_RPM` | AI 시그널 분당 호출 상한 |
| 로깅 | `APP_LOG_LEVEL` (기본 `INFO`) | `com.nowini` 패키지 로거 |

YAML 구조 자체를 바꾸고 싶다면 [`src/main/resources/application.example.yml`](src/main/resources/application.example.yml) 복사해 `application-local.yml` 작성.

## 패키지 구조 (도메인 지향)

```
com.nowini/
├── ApiApplication.java
├── ai/            Gemini 클라이언트 + RAG 파이프라인 + 메트릭 + 레드팀 테스트
├── auth/          Supabase JWT Resource Server 검증 + 계정 삭제 흐름
├── bookmark/      북마크 CRUD
├── cache/         Redis 캐시 계층
├── common/        공통 유틸 (metrics, prompt loader, 예외 등)
├── legal/         투자 자문 금지어 가드 (4-level guard: forbidden-terms.json)
├── market/        시장 대시보드 (지수, VIX, FX, 뉴스)
├── news/          뉴스 수집·번역·캐시
├── feedback/      피드백 CRUD + Resend 이메일 알림
├── notification/  Web Push + 알림 중복 제거 (dedup + news)
└── stock/         시세(Yahoo primary / Finnhub fallback) + 지표(ta4j) + 시장 상태
```

## DB 마이그레이션 (Flyway)

- 파일: `src/main/resources/db/migration/V{N}__*.sql`
- 실행: 앱 기동 시 자동 (`spring.flyway.enabled=true`)
- **새 migration 추가 시 규칙**:
  - 다음 번호 (`V15__...`) 사용
  - 프로덕션(Supabase) 이 이미 적용한 migration 은 **수정 금지** (체크섬 불일치)
  - Supabase 전용 객체(`auth.users`, `anon` / `authenticated` / `service_role` 롤, RLS 정책)는 **테스트 환경 호환 stub** 이 [`src/test/resources/init-supabase-compat.sql`](src/test/resources/init-supabase-compat.sql) 에 있어 그대로 사용 가능
- Testcontainers 가 Flyway 실행 전에 위 init script 를 한 번 돌립니다 ([`TestcontainersConfiguration.java`](src/test/java/com/nowini/TestcontainersConfiguration.java))

## 테스트

- Spring Boot Test + Testcontainers (Postgres + Redis)
- 레드팀 테스트 (`RedTeamPromptInjectionTest`) — 금지어 가드 검증
- 메트릭 테스트 (`*MetricsTest`, `ActuatorExposureTest`)

```bash
./gradlew test                                   # 전체
./gradlew test --tests "*.YahooFinanceClientQuoteTest"  # 특정 클래스
```

## 코딩 컨벤션

- 클래스: `PascalCase`, 메서드: `camelCase`, 상수: `UPPER_SNAKE_CASE`
- 패키지: `lowercase.dot` (`com.nowini.stock`)
- DTO: `*Request` / `*Response`
- 도메인 주도 패키지 레이아웃 유지 (`stock/`, `market/`, ...)

루트 [`CLAUDE.md`](../../CLAUDE.md) 의 Backend 섹션이 SoR.

## 배포

- **플랫폼**: Render — [`Dockerfile`](Dockerfile) 기반
- **DB**: Supabase (PostgreSQL). Flyway 가 기동 시 자동 적용
- **Cache**: Upstash Redis (TLS)
- **관측**: Actuator `/actuator/health`, `/actuator/prometheus`

## 면책 원칙 가드 (4-Level)

`legal/` 패키지 + [`.github/workflows/forbidden-terms.yml`](../../.github/workflows/forbidden-terms.yml) 이 4단계 가드(프롬프트 / 런타임 필터 / 로그 / CI 스캔) 중 Level 1~4 를 담당합니다. 자세한 배경은 [`docs/planning/07-legal-compliance.md`](../../docs/planning/07-legal-compliance.md).
