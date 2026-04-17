# 09. API 키 발급 & 환경변수 설정 가이드

> 로컬 개발 및 운영 배포에 필요한 외부 서비스 가입 → API 키 발급 → 환경변수 설정 절차.

---

## 전제 조건

- GitHub 계정 (대부분의 서비스가 GitHub 로그인 지원)
- 신용카드 **불필요** (모든 서비스 무료 플랜 사용)

---

## 1. Finnhub (주식 시세 / 프로필 / 뉴스)

| 항목 | 내용 |
|------|------|
| **용도** | 종목 검색, 기업 프로필, 실시간 시세(15분 지연), 종목/시장 뉴스, 심볼 목록 |
| **무료 한도** | 60 req/min |
| **주의** | `/stock/candle` 은 무료 플랜에서 403 (유료 $49.99/mo 이상). 캔들은 Twelve Data 사용 |

### 발급 절차

1. https://finnhub.io 접속
2. **Sign Up** → 이메일 가입 또는 Google/GitHub 로그인
3. 가입 완료 후 **Dashboard** 자동 이동
4. 화면 중앙의 **API Key** 복사

### 환경변수

```
# apps/api/.env.local
FINNHUB_API_KEY=your_finnhub_api_key_here
```

---

## 2. Twelve Data (OHLCV 캔들 차트)

| 항목 | 내용 |
|------|------|
| **용도** | OHLCV 캔들 데이터 (일봉/분봉) — 차트 렌더링용 |
| **무료 한도** | 800 req/day, 8 req/min |
| **배경** | Finnhub `/stock/candle` 이 무료 플랜 403이라 대안으로 채택 |

### 발급 절차

1. https://twelvedata.com 접속
2. **Sign Up** → 이메일 가입 또는 Google 로그인
3. 이메일 인증 완료
4. **Dashboard** → 좌측 메뉴 **API Keys** 클릭
5. 기본 생성된 API Key 복사 (또는 **Generate New Key**)

### 환경변수

```
# apps/api/.env.local
TWELVE_DATA_API_KEY=your_twelve_data_api_key_here
```

---

## 3. FMP — Financial Modeling Prep (급등/급락 종목)

| 항목 | 내용 |
|------|------|
| **용도** | Market Movers (biggest-gainers / biggest-losers) |
| **무료 한도** | 250 req/day |
| **배경** | Finnhub 30건 parallel quote 방식이 rate limit 초과 → FMP 2건 호출로 교체 |

### 발급 절차

1. https://site.financialmodelingprep.com 접속
2. **Get Free API Key** 또는 **Sign Up** 클릭
3. 이메일 가입 → 이메일 인증
4. 로그인 후 **Dashboard** → API Key 표시됨
5. API Key 복사

### 환경변수

```
# apps/api/.env.local
FMP_API_KEY=your_fmp_api_key_here
```

### 참고

- 엔드포인트: `https://financialmodelingprep.com/stable/biggest-gainers?apikey={key}`
- "stable" 경로 사용 (legacy `/api/v3/` 경로는 무료 플랜에서 제한될 수 있음)

---

## 4. Google Gemini (AI 분석 / 뉴스 번역)

| 항목 | 내용 |
|------|------|
| **용도** | 종목 AI 분석 시그널, 뉴스 한국어 번역/요약 |
| **무료 한도** | 15 RPM, 1M tokens/day (Gemini 2.5 Flash) |
| **모델** | `gemini-2.5-flash` |

### 발급 절차

1. https://aistudio.google.com 접속
2. Google 계정으로 로그인
3. 좌측 메뉴 **Get API Key** 클릭 (또는 상단 바)
4. **Create API Key** → 기존 GCP 프로젝트 선택 또는 새 프로젝트 자동 생성
5. 생성된 API Key 복사

### 환경변수

```
# apps/api/.env.local
GEMINI_API_KEY=your_gemini_api_key_here
```

### 주의

- API Key 는 GCP 프로젝트에 귀속됨. 여러 키 생성 가능
- 무료 한도 초과 시 429 에러. Redis 캐시로 호출 최소화

---

## 5. Supabase (PostgreSQL DB + Auth)

| 항목 | 내용 |
|------|------|
| **용도** | PostgreSQL 데이터베이스, Phase 4부터 Auth (JWT 발급) |
| **무료 한도** | 500MB DB, 50K MAU (Auth), 2 프로젝트 |

### 발급 절차

1. https://supabase.com 접속
2. **Start your project** → GitHub 로그인
3. **New Project** 생성
   - Organization 선택 (없으면 자동 생성)
   - Project name: `ai-stock-advisor`
   - Database password: **반드시 메모** (이후 변경 불가, 재생성만 가능)
   - Region: `Northeast Asia (ap-northeast)` 권장
4. 프로젝트 생성 완료 (1~2분 소요)

### DB 연결 정보 확인

1. **Settings** → **Database** 클릭
2. **Connection string** 섹션에서 **Transaction** 모드 선택 (Render/Fly.io 호환)
3. 확인할 값:
   - Host: `aws-0-ap-northeast-2.pooler.supabase.com`
   - Port: `6543` (Transaction Pooler)
   - User: `postgres.{project-ref}`
   - Password: 프로젝트 생성 시 설정한 값
   - Database: `postgres`

### Publishable Key 확인 (Phase 4 Auth용)

1. **Settings** → **API** 클릭
2. **Project API keys** 섹션:
   - `publishable` (= 구 anon key): FE에서 사용 — 브라우저에 노출 가능
   - `secret` (= 구 service_role key): 서버 전용 — 절대 노출 금지

> BE(Spring Boot)는 별도의 secret key 없이 JWKS endpoint(`{SUPABASE_URL}/auth/v1/.well-known/jwks.json`)로 JWT 서명을 검증합니다.

### Google OAuth 설정 (Phase 4 소셜 로그인)

FE의 "Google로 계속" 버튼이 동작하려면 Google Cloud Console + Supabase 양쪽 설정이 필요합니다.

#### A. Google Cloud Console — OAuth 동의 화면

1. https://console.cloud.google.com 접속 → Google 계정 로그인
2. 프로젝트가 없으면 상단 프로젝트 선택 → **New Project** 생성 (예: `ai-stock-advisor`)
3. 좌측 메뉴 **APIs & Services** → **OAuth consent screen**
4. User Type: **External** 선택 → **Create**
5. 앱 정보 입력:
   - App name: `AI Stock Advisor`
   - User support email: 본인 이메일
   - Developer contact email: 본인 이메일
6. **Scopes** → **Add or remove scopes** → `email`, `profile`, `openid` 선택 → **Save**
7. **Test users** → 본인 이메일 추가 (앱이 "Testing" 상태일 때 테스트 가능)
8. **Save and Continue** → 완료

#### B. Google Cloud Console — OAuth Client ID 생성

1. 좌측 메뉴 **APIs & Services** → **Credentials**
2. 상단 **+ CREATE CREDENTIALS** → **OAuth client ID**
3. Application type: **Web application**
4. Name: `AI Stock Advisor` (자유)
5. **Authorized JavaScript origins** 추가:
   - `http://localhost:3000` (로컬 개발)
   - `https://your-app.vercel.app` (배포 후 추가)
6. **Authorized redirect URIs** 추가:
   - `https://{project-ref}.supabase.co/auth/v1/callback`
   > `{project-ref}`는 Supabase Dashboard URL에서 확인 (예: `abcdefghij.supabase.co`)
7. **Create** 클릭
8. 팝업에서 **Client ID** 와 **Client Secret** 복사

#### C. Supabase Dashboard — Google Provider 활성화

1. https://supabase.com/dashboard → 프로젝트 선택
2. 좌측 **Authentication** → **Providers**
3. **Google** 클릭
4. **Enable Sign in with Google** 토글 ON
5. **Client ID**: B단계에서 복사한 값 붙여넣기
6. **Client Secret**: B단계에서 복사한 값 붙여넣기
7. **Save**

#### 주의

- 앱이 Google에서 **Testing** 상태인 동안에는 **Test users에 등록된 계정만** 로그인 가능
- 실서비스 공개 시: OAuth consent screen → **Publishing status** → **In production** 변경 → Google 심사 필요 (간단한 앱은 보통 즉시 통과)
- Redirect URI를 틀리면 `redirect_uri_mismatch` 에러 발생 — Google Console과 Supabase 양쪽의 URI가 정확히 일치해야 함

### 환경변수

```
# apps/api/.env.local
DATABASE_URL=jdbc:postgresql://aws-0-ap-northeast-2.pooler.supabase.com:6543/postgres?prepareThreshold=0
DATABASE_USERNAME=postgres.your-project-ref
DATABASE_PASSWORD=your_database_password

# Phase 4 Auth — JWKS 기반 JWT 검증 (secret key 불필요)
SUPABASE_URL=https://your-project-ref.supabase.co
```

```
# apps/web/.env.local (Phase 4 Auth)
NEXT_PUBLIC_SUPABASE_URL=https://your-project-ref.supabase.co
NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY=your_publishable_key
```

> Google OAuth의 Client ID / Client Secret은 **Supabase 콘솔에만 입력**합니다. 코드나 환경변수에 넣을 필요 없습니다.

### 로컬 개발 시

Docker Compose로 PostgreSQL을 직접 실행하는 경우:

```
DATABASE_URL=jdbc:postgresql://localhost:5432/aistockadvisor
DATABASE_USERNAME=dev
DATABASE_PASSWORD=dev
```

---

## 6. Upstash (Redis 캐시)

| 항목 | 내용 |
|------|------|
| **용도** | 시세/지표/뉴스 캐시, rate limit 카운터 |
| **무료 한도** | 10,000 commands/day, 256MB |

### 발급 절차

1. https://console.upstash.com 접속
2. GitHub 또는 Google 로그인
3. **Create Database** 클릭
   - Name: `ai-stock-advisor`
   - Type: `Regional`
   - Region: `ap-northeast-1` (Tokyo) 권장
   - TLS: Enabled (기본값)
4. 생성 완료

### 연결 정보 확인

1. 생성된 DB 클릭 → **Details** 탭
2. **Redis** 섹션에서 확인 (REST API 섹션 아님!):
   - Endpoint: `{name}-{id}.upstash.io`
   - Port: `6379`
   - Password: 표시된 값 복사

### 환경변수

```
# apps/api/.env.local
REDIS_URL=rediss://default:your_password@your-endpoint.upstash.io:6379
```

### 주의

- `rediss://` (s 두 개) — TLS 연결 필수
- `https://` (REST API URL) 과 혼동 금지 — Spring Data Redis 는 REST 미지원
- 로컬 Docker Redis 사용 시: `REDIS_URL=redis://localhost:6379`

---

## 7. Alpha Vantage (현재 미사용, fallback 예비)

| 항목 | 내용 |
|------|------|
| **용도** | OHLCV 캔들 legacy fallback (현재 Twelve Data로 대체) |
| **무료 한도** | 25 req/day, 5 req/min |

### 발급 절차

1. https://www.alphavantage.co 접속
2. **Get your free API key** 클릭
3. 이름, 이메일, 사용 용도 입력
4. API Key 즉시 발급 (이메일 인증 없음)

### 환경변수

```
# apps/api/.env.local (현재 미사용, 필요 시 활성화)
ALPHAVANTAGE_API_KEY=your_alphavantage_api_key_here
```

---

## 8. Vercel (FE 배포)

| 항목 | 내용 |
|------|------|
| **용도** | Next.js 프론트엔드 배포, Analytics |
| **무료 한도** | Hobby 플랜 (개인 프로젝트 무제한) |

### 설정 절차

1. https://vercel.com 접속 → GitHub 로그인
2. **New Project** → GitHub repo Import
3. Root Directory: `apps/web`
4. Framework Preset: `Next.js` (자동 감지)
5. Environment Variables 설정:

```
NEXT_PUBLIC_API_BASE_URL=https://your-backend.onrender.com
NEXT_PUBLIC_SITE_URL=https://your-app.vercel.app
```

> API Key 발급은 불필요. GitHub 연동만으로 배포 가능.

---

## 9. Render (BE 배포)

| 항목 | 내용 |
|------|------|
| **용도** | Spring Boot 백엔드 배포 |
| **무료 한도** | Free (512MB, 15분 idle 시 sleep) |

### 설정 절차

1. https://render.com 접속 → GitHub 로그인
2. **New Web Service** → GitHub repo 연결
3. Root Directory: `apps/api`, Runtime: `Docker`
4. Environment Variables: 위 1~6번에서 발급받은 키 모두 입력

> API Key 발급은 불필요. GitHub 연동만으로 배포 가능.

---

## Quick Reference — 전체 환경변수 맵

### BE (`apps/api/.env.local`)

| 변수 | 서비스 | 필수 | 발급 위치 |
|------|--------|:----:|-----------|
| `DATABASE_URL` | Supabase | ✅ | Settings → Database → Connection string |
| `DATABASE_USERNAME` | Supabase | ✅ | (위와 동일) |
| `DATABASE_PASSWORD` | Supabase | ✅ | 프로젝트 생성 시 설정값 |
| `REDIS_URL` | Upstash | ✅ | Console → DB → Details → Redis 섹션 |
| `FINNHUB_API_KEY` | Finnhub | ✅ | Dashboard → API Key |
| `TWELVE_DATA_API_KEY` | Twelve Data | ✅ | Dashboard → API Keys |
| `FMP_API_KEY` | FMP | ✅ | Dashboard → API Key |
| `GEMINI_API_KEY` | Google AI Studio | ✅ | Get API Key → Create |
| `SUPABASE_JWT_SECRET` | Supabase | Phase 4 | Settings → API → JWT Secret |
| `ALPHAVANTAGE_API_KEY` | Alpha Vantage | ❌ | 미사용 (fallback 예비) |
| `SERVER_PORT` | - | ✅ | `8080` (고정) |
| `SPRING_PROFILES_ACTIVE` | - | ✅ | `local` (로컬) / `prod` (운영) |
| `CORS_ALLOWED_ORIGINS` | - | ✅ | `http://localhost:3000` (로컬) |
| `APP_LOG_LEVEL` | - | - | `DEBUG` (로컬) / `INFO` (운영) |

### FE (`apps/web/.env.local`)

| 변수 | 서비스 | 필수 | 값 |
|------|--------|:----:|-----|
| `NEXT_PUBLIC_API_BASE_URL` | - | ✅ | `http://localhost:8080/api/v1` (로컬) |
| `NEXT_PUBLIC_SITE_URL` | - | ✅ | `http://localhost:3000` (로컬) |
| `NEXT_PUBLIC_SUPABASE_URL` | Supabase | Phase 4 | `https://{ref}.supabase.co` |
| `NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY` | Supabase | Phase 4 | Settings → API → anon/public |

---

## 로컬 개발 빠른 시작

```bash
# 1. 환경변수 파일 복사
cp apps/api/.env.example apps/api/.env.local
cp apps/web/.env.example apps/web/.env.local

# 2. 각 .env.local 파일에 위에서 발급받은 키 입력

# 3. Docker로 로컬 DB/Redis 실행 (Supabase/Upstash 대신 사용 가능)
docker compose up -d

# 4. 개발 서버 실행
make dev
```

> 로컬에서 Docker로 PostgreSQL + Redis를 실행하면 Supabase/Upstash 발급 없이도 개발 가능합니다.
> 외부 API 키 (Finnhub, Twelve Data, FMP, Gemini) 는 각각 발급이 필요합니다.
