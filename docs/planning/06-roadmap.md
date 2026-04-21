# 06. 단계별 로드맵

> 현재 단계: **v0.1.0 — Beta** (2026-04-20). Phase 0~4.5 완료, Phase 5+ 미확정.
> 릴리스 노트: [GitHub Releases](https://github.com/wonseok-han/ai-stock-advisor/releases)
> 기능별 완료 기록: [`docs/archive/`](../archive/)

---

## Phase 0: 기획 ✅ 완료

- [x] 서비스 포지셔닝 결정 (A안: 분석 도구)
- [x] 기술 스택 결정 (Next.js 16 + Spring Boot 3.5 / Java 21)
- [x] 데이터 소스 선정
- [x] AI 전략 수립
- [x] MVP 기능 명세 확정
- [x] DB 스키마 (Flyway V1~V14 로 구현)
- [x] 법적 고지 문구 확정 (`/legal/terms`, `/legal/privacy`)

---

## Phase 1: MVP 코어 데이터 파이프라인 ✅ 완료

**목표:** 로그인 없이, 한 개 종목의 정적/동적 데이터를 보여주는 것.

- [x] 백엔드 프로젝트 초기화 (Gradle, Java 21, Spring Boot 3.5.13)
- [x] 공통 설정 (Spring Security, CORS, Actuator, Flyway)
- [x] `ExternalApiClient` 추상화 (Yahoo primary + Finnhub fallback)
- [x] Redis 캐시 설정 (Upstash TLS)
- [x] Rate limiter (Bucket4j) 연동 + AI 시그널 분당 상한
- [x] 주요 API: `/api/stocks/search`, `/profile`, `/quote`, `/candles`, `/indicators`, `/news`
- [x] 기본 에러 처리 + 로깅
- [x] 프론트 프로젝트 초기화 (Next.js 16, App Router, TS, Tailwind 4)
- [x] 종목 상세 페이지 (검색, 기본 정보, 차트, 지표 카드, 뉴스)
- [x] 로딩/에러 상태 UI
- [x] 전역 면책 고지 (`disclaimer-footer`)

---

## Phase 2: AI 레이어 ✅ 완료

**목표:** 종목 상세 페이지에 AI 분석 카드 추가.

- [x] `LlmClient` 인터페이스 + Gemini 2.5 Flash 구현체
- [x] 프롬프트 빌더 + externalize (classpath prompts)
- [x] 면책 4단계 가드 (프롬프트 / 런타임 필터 / 로그 / CI 스캔)
- [x] JSON 응답 파싱·검증·재시도(1회) 로직
- [x] Postgres + Redis hybrid 캐시 (AI 1h, 뉴스 24h)
- [x] 뉴스 번역·요약 파이프라인 (Gemini)
- [x] AI 분석 카드 + 뉴스 번역본 토글
- [x] 레드팀 테스트 (`RedTeamPromptInjectionTest`)
- [x] Micrometer 관측성 (llm_call_count_total 등)

---

## Phase 3: 시장 대시보드 ✅ 완료

**목표:** 메인 페이지에 시장 전체 스냅샷.

- [x] `/api/market/overview` (주요 지수, VIX, USD/KRW, 10Y 금리)
- [x] `/api/market/news`, `/api/market/movers`
- [x] 메인 페이지 위젯 (지수 카드, VIX, 환율, 뉴스 피드, 급등락)
- [x] 캐시 전략 (5~15분 TTL)

---

## Phase 4: 회원 / 북마크 / 알림 ✅ 완료

**목표:** 개인화.

- [x] Supabase Auth (이메일 + 구글 OAuth) + Spring Security JWT Resource Server
- [x] 북마크 CRUD + 마이페이지
- [x] Web Push 구독·해지 (VAPID) + `push_subscriptions`, `notification_settings`
- [x] 알림 조건 평가 + 스케줄러 (가격 변동, 새 뉴스, 시그널 변화)

---

## Phase 4.5: 운영 강화 ✅ 완료

Phase 4 이후 베타 전까지 누적된 개선 사항. 기능별 세부는 [`docs/archive/2026-04/`](../archive/2026-04/).

| # | 주제 | 요약 |
|---|---|---|
| 4.5.1 | candle DB · 마이페이지 · rate limiter | OHLCV 장기 보관 + 마이페이지 확장 + AI 호출 상한 |
| 4.5.2 | 알림 중복 제거 (dedup) | 히스테리시스 + 쿨다운으로 푸시 스팸 제거 |
| 4.5.3 | 뉴스 알림 (notification-news) | 북마크 종목의 새 뉴스 푸시 |
| 4.5.4 | 비밀번호 재설정 | `/forgot-password`, `/reset-password` (Supabase PKCE) |
| 4.5.5 | 베타 피드백 채널 | `/feedback` + Supabase `feedback` 테이블 + RLS |
| 그 외 | stock-quote (Yahoo primary), 뉴스 번역 fallback, V13 미사용 테이블 drop | — |

---

## 🟢 현재 단계: v0.1.0 Beta 운영

**완료 조건:** 배포된 URL 에서 실제 사용자(최초: 본인) 가 기능 전반을 사용, 피드백/에러를 수집.

**관찰 포인트 (미확정):**

- 운영 관찰성 (Sentry / PostHog / Admin feedback 조회)
- 베타 사용자 피드백 수집 (`/feedback` 제출 내용 모니터링)
- 비용 실측 (Gemini API, Supabase 용량, Upstash 호출량)
- 장애 대응 경험 축적

이 단계의 결과로 **Phase 5+ 중 어느 방향으로 갈지 결정**합니다.

---

## Phase 5+: 향후 과제 (미확정)

아래는 후보일 뿐이며, Beta 운영 결과와 사용자 피드백에 따라 우선순위가 바뀝니다.

- 운영 관찰성 (Sentry + PostHog + Admin feedback 대시보드)
- 포트폴리오 시뮬레이션 (가상 매매)
- 백테스팅 (AI 시그널 신뢰도 실증)
- 한국 주식 확장
- 모바일 앱 (React Native or Flutter + FCM)
- 고급 지표 (이치모쿠, 피보나치, Elliott Wave)
- 소셜/커뮤니티
- 수익화 (프리미엄, 제휴) — **법적 재검토 필수** ([07-legal-compliance.md §7.7](07-legal-compliance.md))
- 실시간 스트리밍 시세 (Polygon.io 유료)

---

## 마일스톤 체크리스트 (한 줄 요약)

| Phase | 한 줄 목표 | 상태 |
|---|---|---|
| 0 | "이 문서로 개발 시작해도 되겠다" | ✅ |
| 1 | "AAPL 페이지가 차트+지표+뉴스로 채워진다" | ✅ |
| 2 | "AI가 AAPL에 대해 말이 되는 소리를 한다" | ✅ |
| 3 | "메인에서 오늘 시장 분위기가 한눈에 보인다" | ✅ |
| 4 | "내가 찜한 종목에 변화가 생기면 알림이 온다" | ✅ |
| 4.5 | "베타에 내놓아도 부끄럽지 않다" | ✅ |
| **Beta (v0.1.x)** | **"사람들이 실제로 쓰고, 피드백이 쌓인다"** | 🟢 진행 중 |
| 5+ | "사용자 피드백을 보고 다음을 정한다" | ⏳ 미확정 |
