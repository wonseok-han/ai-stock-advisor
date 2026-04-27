# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [0.3.0-beta] - 2026-04-27

피드백 API Spring Boot 전환 + Resend 이메일 알림. 18 files changed, +713 / -566 lines.

### Added

- **피드백 Spring Boot API 전환 + Resend 이메일 알림** — PR #39
  - Supabase Edge Function → Spring Boot REST API 전환 (`/api/v1/feedback`)
  - Resend API 이메일 알림 (피드백 접수 시 관리자에게 자동 발송)
  - FeedbackController + FeedbackService + ResendEmailService
  - FE: API 엔드포인트 Spring Boot로 전환

### Changed

- **README 최신화 + `/release` 커맨드 추가** — PR #40
  - 루트·FE·BE README 현행화 (기능 목록, 기술 스택, 배포 정보)
  - `/release` Claude 커맨드: changelog 기반 GitHub Release 자동화
  - `.claude/tech-stack.md`: Resend, Web Push, Monitoring 추가
  - `.github/workflows/release.yml` 자동 워크플로 삭제 (커맨드로 대체)
- **CI forbidden-terms**: accuracy-domain 스캔 제거 (면책 문구 오탐 방지)

### Fixed

- CI 테스트 실패: `priceLabel` KST 접미사 호환 + MockWebServer 호스트 바이패스
- 면책 부인 문구 원복 + CI disclaimer 마커 예외 처리
- 금칙어 "예측" → "방향 일치율" 용어 교체

### Statistics

| 항목 | 수치 |
|------|------|
| 변경 파일 | 18 |
| 추가 라인 | +713 |
| 삭제 라인 | -566 |
| 머지된 PR | #39 ~ #40 (2개) |

---

## [0.2.0-beta] - 2026-04-27

지금이니?! 리브랜딩 + 대규모 기능 확장. 133 files changed, +12,604 / -1,022 lines.

### Added

- **지금이니?! (Nowini) 리브랜딩** — PR #27
  - 로고·파비콘·브랜드명 전면 교체
  - Light / Dark / Brand 3-테마 시스템 구축 및 전역 적용
  - 테마 스위처 UI + CSS 토큰 체계화 (`bg-surface`, `fg-primary`, `border` 등)

- **AI 참고 분석 v2 고도화** — PR #26
  - RAG 파이프라인 개선 (뉴스 + 기술 지표 통합 컨텍스트)
  - 시그널 정합도 측정 인프라 (Match Rate 96%) — PR #25

- **Yahoo Finance 마이그레이션** — PR #29
  - YahooFinanceClient v8 chart API (인트라데이 5m 캔들, API key 불필요)
  - CandleService: Yahoo Finance 1차 → TwelveData fallback
  - MarketOverviewService: 3-tier fallback (Finnhub → Yahoo → TwelveData)
  - TimeFrame enum `yahooInterval()` 추가
  - TwelveData를 최후 fallback으로 다운그레이드

- **종목 상세 강화** — PR #30
  - CompanyOverviewPanel: 시가총액, P/E, EPS, 52주 고저, 배당수익률
  - YahooFinanceClient `quoteSummary` API 통합 (v10 crumb+cookie 인증)
  - CompanyOverview 도메인 모델 + CompanyOverviewService

- **대시보드 확장** — PR #31
  - 섹터 퍼포먼스 패널 (11개 GICS 섹터, FMP API)
  - 매크로 지표 카테고리 (지수·통화·원자재·채권 분류)
  - SectorPerformanceService + FmpClient 확장

- **애널리스트 평점·목표가** — PR #32
  - RatingGauge: 컨센서스 등급 (Strong Buy ~ Strong Sell)
  - PriceTargetBar: 목표가 범위 vs 현재가 시각화
  - EarningsHistory: 분기별 실적 (예상 vs 실제, Beat/Miss 표시)
  - AnalystEstimatesService + YahooFinanceClient `analystEstimates()`

- **AI 시그널 UX 개선** — PR #34
  - 이중관점 구조: 단기(1~5일) + 장기(1~3개월) 시그널 분리
  - SignalGuide: "이 분석은 이렇게 읽으세요" 가이드
  - ConfidenceTooltip: 확신도 지표 설명 툴팁
  - 테마별 시그널 색상 보정

- **헤더 툴박스 + 플로팅 FAB + 스낵바**
  - FloatingToolbox: 모바일 하단 FAB (북마크·알림 빠른접근)
  - Snackbar 시스템: Zustand 기반 전역 토스트 알림
  - 마이페이지 탭 리디자인 (북마크·알림·계정 섹션 개편)

### Changed

- **API 캐시 최적화** — PR #33
  - 적응형 TTL: 장중(짧은 TTL) / 장외(긴 TTL) 자동 전환
  - `MarketStatusResolver.durationUntilNextOpen()` 기반 캐시 만료 계산
  - 2중 캐시: Redis L1 + 로컬 인메모리 L2 캐시 구조

- **Yahoo Finance 429 대응**
  - quoteSummary per-ticker synchronized lock (cache stampede 방지)
  - curlFetch 429 시 불필요한 proxy rotation 제거
  - crumb Redis 영속화 수정 (`crumbRotated` 플래그로 evict 루프 방지)
  - 지수 4개 fallback을 TwelveData 우선으로 변경하여 Yahoo 요청량 감소

- **라이트모드 UI 폴리싱** — PR #35
  - 에메랄드 톤 배경 체계 (브랜드 테마 그라데이션)
  - 스켈레톤 로딩 토큰 분리 (테마별 독립 색상)
  - 대시보드 카드 그룹화 + 간격 통일
  - InfoTooltip, PanelLoading 공용 UI 컴포넌트 추가

- **CI forbidden-terms.yml**: accuracy-domain 스캔 제거 (면책 문구 오탐 방지)
- **CLAUDE.md** 분리: `tech-stack.md`, `conventions.md`, `workflow.md`로 모듈화

### Fixed

- `/my` 페이지 `useSearchParams` Suspense boundary 누락 → Vercel 빌드 실패 수정
- MarketStatusResolver `priceLabel` KST 표기 호환성 테스트 수정
- YahooFinanceClient MockWebServer 테스트 호환성 (`chartHosts` 인스턴스 필드)

### Statistics

| 항목 | 수치 |
|------|------|
| 변경 파일 | 133 |
| 추가 라인 | +12,604 |
| 삭제 라인 | -1,022 |
| 머지된 PR | #25 ~ #38 (14개) |
| PDCA 아카이브 | 7개 기능 완료 |

---

## [0.1.0] - 2026-04-20

최초 베타 릴리즈. 인증·북마크·알림·캔들 DB·마이페이지·계정관리 완성. 75 files changed, +3,424 / -50 lines.

### Added

- **Supabase Auth 통합** — PR #24
  - 이메일/비밀번호 로그인·회원가입 + Google OAuth
  - JWT 토큰 관리 (access + refresh)
  - Spring Security JWT Resource Server (RS256 + ES256)
  - Two-chain SecurityFilterChain (public/protected 분리)
  - FE: AuthProvider, LoginForm, SignupForm, SocialLogin, UserMenu, AuthGuardModal

- **북마크** — PR #24
  - `POST/DELETE/GET /api/v1/bookmarks` + check 엔드포인트
  - Optimistic UI + BookmarkButton 토글
  - 마이페이지 BookmarkGrid 카드 레이아웃

- **Web Push 알림** — PR #24
  - VAPID 기반 구독 (`/api/v1/push/subscribe`)
  - 종목별 조건 설정 (가격 변동 ±%, 뉴스, 시그널)
  - `@Scheduled` 15분 주기 체크 + 알림 발송
  - Notification dedup: Hysteresis + 4시간 cooldown (스팸 방지)

- **마이페이지 & 계정관리** — PR #24
  - ProfileSection, BookmarkGrid, NotificationSection, AccountSection
  - 계정 삭제 (Soft Delete, 2년 보관, Supabase Auth ban)
  - 재활성화: `POST /api/v1/auth/reactivate`

- **Candle DB (on-demand)** — PR #24
  - `candles` 테이블 (Flyway V8) + Yahoo Finance v8 API
  - DB-first → Yahoo fallback → async persist
  - 일봉 배치 스케줄러 (MON-FRI 22:00 UTC)

- **차트 개선** — PR #24
  - 볼륨 히스토그램 (green up / red down)
  - 1W: 일봉 × 5일, 1M/3M/1Y: DB 일봉, 5Y: 주간 집계

- **Rate Limiter** — IP 기반 Token Bucket (60 req/min)

### Changed

- SecurityFilterChain: 단일 체인 → Two-chain (@Order 1, 2) 분리
  - public API는 JWT 검증 건너뜀 (성능)
- VAPID key: FE 환경변수 → BE API endpoint (`/api/v1/push/vapid-key`)
- OAuth callback: client-side `page.tsx` → server-side `route.ts` (보안)
- BookmarkResponse `price`: double → BigDecimal (금융 정밀도)

### Fixed

- Public API 401 오류 → Two-chain 분리로 해결
- Hibernate `ddl-auto=create-drop` → `validate` 전환 (데이터 유실 방지)
- Supabase Bulk Insert timeout → 배치 1000건 분할
- CORS protected endpoint 미적용 → WebCorsConfig bean
- Notification 중복 발송 → Hysteresis + cooldown 메커니즘

### Statistics

| 항목 | 수치 |
|------|------|
| 변경 파일 | 75 |
| 추가 라인 | +3,424 |
| 삭제 라인 | -50 |
| BE 엔드포인트 | 9개 |
| FE 컴포넌트 | 7개 |
| DB 마이그레이션 | V6 ~ V10 (5개) |

---

## Contributors

- **wonseok-han** — Implementation, testing, deployment
- **Claude (AI)** — Design, architecture, code review

---

## License

Proprietary - 지금이니?! (Nowini)
