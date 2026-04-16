# 08. 배포 가이드

> AI Stock Advisor 운영 환경 배포 구성 및 절차.

---

## 1. 인프라 구성

| 레이어 | 플랫폼 | 플랜 | 월 비용 | 리전 |
|---|---|---|---:|---|
| **FE** | Vercel | Hobby (무료) | $0 | Auto |
| **BE** | Render | Free (512MB, cold start) | $0 | Oregon (US West) |
| **DB** | Supabase | Free (500MB) | $0 | ap-northeast (설정값 확인) |
| **Cache** | Upstash Redis | Free (10k cmd/day) | $0 | ap-northeast-1 (도쿄) |

### 아키텍처

```
[Vercel - Next.js]
        │
        │ HTTPS (NEXT_PUBLIC_API_BASE_URL)
        ▼
[Render - Spring Boot]
        │
        ├── JDBC (Session Pooler) ──▶ [Supabase PostgreSQL]
        └── Redis (TLS) ───────────▶ [Upstash Redis]
```

### 제약 사항

- **Render Free**: 15분 idle 시 sleep → cold start 30~60초 (Spring Boot JVM 특성)
- **Supabase Free**: 7일 미접속 시 pause (콘솔에서 1클릭 재개)
- **Upstash Free**: 일일 10,000 커맨드 제한, 256MB 저장소

---

## 2. 외부 계정

| 서비스 | 가입 URL | 가입 방법 | 카드 필요 |
|---|---|---|:---:|
| **Vercel** | https://vercel.com | GitHub 로그인 | 불필요 |
| **Render** | https://render.com | GitHub 로그인 | 불필요 |
| **Supabase** | https://supabase.com | GitHub 로그인 | 불필요 |
| **Upstash** | https://upstash.com | GitHub 로그인 | 불필요 |

---

## 3. 환경변수

### 3.1 BE (Render)

| 변수 | 설명 | 예시 / 형식 |
|---|---|---|
| `DATABASE_URL` | Supabase **Transaction Pooler** JDBC URL | `jdbc:postgresql://aws-1-ap-southeast-2.pooler.supabase.com:6543/postgres?prepareThreshold=0` |
| `DATABASE_USERNAME` | Supabase Pooler 사용자 | `postgres.{project-ref}` |
| `DATABASE_PASSWORD` | Supabase DB 비밀번호 | (프로젝트 생성 시 설정값) |
| `REDIS_URL` | Upstash Redis **프로토콜 URL** | `rediss://default:{password}@{host}.upstash.io:6379` |
| `GEMINI_API_KEY` | Google Gemini API 키 | |
| `FINNHUB_API_KEY` | Finnhub API 키 | |
| `TWELVE_DATA_API_KEY` | Twelve Data API 키 | |
| `CORS_ALLOWED_ORIGINS` | Vercel FE 도메인 | `https://{app}.vercel.app` |
| `SUPABASE_JWT_SECRET` | Phase 4 인증 시 필요 (현재 빈값) | |

### 3.2 FE (Vercel)

| 변수 | 설명 | 예시 / 형식 |
|---|---|---|
| `NEXT_PUBLIC_API_BASE_URL` | Render BE URL | `https://{service}.onrender.com` |

---

## 4. 배포 절차

### 4.1 Supabase (DB)

1. 프로젝트 생성 (리전 선택, DB 비밀번호 설정)
2. **Settings → Database → Connection string → Transaction** 모드 선택
3. 호스트/유저/비밀번호 확인

**주의 — Direct vs Pooler**:

| 연결 방식 | 호스트 형태 | 포트 | 유저명 | 용도 |
|---|---|---|---|---|
| Direct | `db.{ref}.supabase.co` | 5432 | `postgres` | IPv4 전용, Render 불가 |
| Session Pooler | `aws-*.pooler.supabase.com` | 5432 | `postgres.{ref}` | pool_size 한도 작아 배포 반복 시 MaxClients 에러 발생 |
| **Transaction Pooler** (사용) | `aws-*.pooler.supabase.com` | 6543 | `postgres.{ref}` | 쿼리 단위 반환, Render 호환 |

> Render Free 는 IPv6 outbound 전용이라 Direct 불가, **Transaction Pooler** 사용.
> JDBC URL 에 `?prepareThreshold=0` 필수 (Transaction Pooler 에서 prepared statement 호환).

### 4.2 Upstash (Redis)

1. Redis DB 생성 (리전: `ap-northeast-1`)
2. **Details 탭**에서 Endpoint / Port / Password 확인
3. URL 조합: `rediss://default:{password}@{endpoint}:6379`

**주의**:
- `rediss://` (s 두 개) = TLS 연결. Upstash 는 TLS 필수
- `https://` (REST API URL) 와 혼동 금지 — Spring Data Redis 는 REST 지원 안 함
- Upstash 콘솔의 "REST API" 섹션이 아닌 **"Redis" 섹션**에서 복사

### 4.3 Render (BE)

1. **New Web Service** → GitHub repo 연결
2. 설정:
   - Root Directory: `apps/api`
   - Runtime: `Docker`
   - Instance Type: `Free`
3. Environment Variables 입력 (§3.1 참조)
4. Create Web Service → 빌드 시작

**Dockerfile 위치**: `apps/api/Dockerfile` (multi-stage, JDK 21 빌드 → JRE 21 런타임)

**JVM 튜닝 (512MB 제한)**:
```
-Xmx256m -Xms128m -XX:MaxMetaspaceSize=100m -Xss256k -XX:+UseSerialGC
```
- SerialGC: 메모리 오버헤드 최소 (ZGC/G1GC 는 512MB 에 OOM)
- 힙 256MB + 메타스페이스 100MB + 네이티브 ~100MB = ~456MB

### 4.4 Vercel (FE)

1. **New Project** → GitHub repo Import
2. 설정:
   - Root Directory: `apps/web`
   - Framework Preset: `Next.js` (자동 감지)
3. Environment Variables 입력 (§3.2 참조)
4. Deploy

### 4.5 배포 순서 (CORS 의존성)

```
1. Supabase DB 생성       → DATABASE_URL 확보
2. Upstash Redis 생성     → REDIS_URL 확보
3. Render BE 배포          → BE URL 확보 (https://{service}.onrender.com)
4. Vercel FE 배포          → NEXT_PUBLIC_API_BASE_URL = BE URL
5. Render CORS 재설정      → CORS_ALLOWED_ORIGINS = Vercel 도메인
6. Render Manual Deploy    → CORS 반영 (환경변수 변경 시 재시작 필요)
```

---

## 5. 배포 검증

```bash
# 1. Health check
curl https://{render-service}.onrender.com/actuator/health
# 기대: {"status":"UP"}

# 2. 실 API 호출
curl https://{render-service}.onrender.com/stocks/AAPL/quote
# 기대: JSON 응답 (시세 데이터)

# 3. 메트릭 노출
curl https://{render-service}.onrender.com/actuator/prometheus | grep llm_call_count
# 기대: llm_call_count_total 시리즈

# 4. Flyway 마이그레이션 확인 (Render Shell 또는 로그)
# 로그에서 "Successfully applied N migrations" 확인

# 5. FE 동작
# Vercel URL 접속 → 종목 검색 → /detail 진입 → 뉴스/AI 시그널 로드
```

---

## 6. 트러블슈팅

| 증상 | 원인 | 해결 |
|---|---|---|
| `Network is unreachable` (DB) | Direct connection + Render IPv6 | Transaction Pooler (6543) 로 변경 |
| `Invalid Redis URL 'https://...'` | Upstash REST API URL 사용 | `rediss://` 프로토콜 URL 로 변경 |
| `Out of memory (over 512Mi)` | JVM 힙/GC 과다 설정 | SerialGC + Xmx200m + MaxMetaspaceSize=150m |
| `Metaspace OOM` | MaxMetaspaceSize 부족 | 150m 이상 확보 (Spring+JPA+WebFlux 필요) |
| `MaxClientsInSessionMode` | Session Pooler pool_size 한도 초과 | Transaction Pooler (6543) + `?prepareThreshold=0` 로 전환 |
| Cold start 30~60초 (128초까지) | Render Free sleep 정책 | 정상 동작, 유료 전환 시 해소 |
| Supabase pause | 7일 미접속 | 콘솔에서 Restore 클릭 |
| CORS 에러 (FE → BE) | CORS_ALLOWED_ORIGINS 미설정 | Render 환경변수 + 재배포 |
| 502 Bad Gateway | cold start 중 요청 | 2분 대기 후 재시도 |

---

## 7. 향후 업그레이드 경로

| 시점 | 변경 | 비고 |
|---|---|---|
| 실사용자 유입 | Render Free → Fly.io ($3/월) 또는 Render Starter ($7/월) | Cold start 제거 |
| Phase 4 (인증) | `SUPABASE_JWT_SECRET` 설정 | Supabase Auth + Spring Security |
| 트래픽 증가 | Upstash Pro 업그레이드 | 10k cmd/day 초과 시 |
| 데이터 500MB 초과 | Supabase Pro ($25/월) | DB 용량 확장 |
