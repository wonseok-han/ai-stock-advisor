# Archive — 2026-04

완료된 PDCA 사이클 문서 보관소.

| Feature | Phase | Match Rate | Started | Archived | 문서 |
|---|---|---:|---|---|---|
| [mvp](mvp/) | Phase 1 | 94% | 2026-04-10 | 2026-04-14 | prd, plan, design, do, analysis, report |
| [phase2-rag-pipeline](phase2-rag-pipeline/) | Phase 2 | 93% | 2026-04-14 | 2026-04-14 | prd, plan, design, analysis, report |
| [phase2.1-metrics-fe-refactor](phase2.1-metrics-fe-refactor/) | Phase 2.1 | 100% | 2026-04-14 | 2026-04-14 | plan, design, analysis, report |
| [phase2.2-prompt-externalization](phase2.2-prompt-externalization/) | Phase 2.2 | 96% | 2026-04-15 | 2026-04-15 | plan, design, analysis, report |
| [market-dashboard](market-dashboard/) | Phase 3 | 95% | 2026-04-16 | 2026-04-16 | plan, design, report |
| [auth](auth/) | Phase 4 | 95% | 2026-04-16 | 2026-04-17 | plan, design, analysis, report |
| [phase4.5-improvements](phase4.5-improvements/) | Phase 4.5 | 96.4% | 2026-04-17 | 2026-04-17 | plan, design, analysis, report |
| [notification-dedup](notification-dedup/) | Phase 4.5.1 | 100% | 2026-04-20 | 2026-04-20 | plan, design, analysis, report |
| [notification-ui-cleanup](notification-ui-cleanup/) | Phase 4.5.2 | 100% | 2026-04-20 | 2026-04-20 | plan, design, analysis, report |
| [notification-news](notification-news/) | Phase 4.5.3 | 100% | 2026-04-20 | 2026-04-20 | plan, design, analysis, report |
| [password-reset](password-reset/) | Phase 4.5.4 | 99% | 2026-04-20 | 2026-04-20 | plan, design, analysis, report |
| [feedback](feedback/) | Phase 4.5.5 | 99% | 2026-04-20 | 2026-04-20 | plan, design, analysis, report |
| [ai-analysis-deepening](ai-analysis-deepening/) | Phase 4.5.6 | 93% | 2026-04-21 | 2026-04-21 | plan, design, analysis, report |
| [yahoo-migration](yahoo-migration/) | Phase 4.5.7 | 100% | 2026-04-23 | 2026-04-23 | plan, design, report |
| [stock-detail-enrichment](stock-detail-enrichment/) | Phase 4.5.8 | 92% | 2026-04-10 | 2026-04-23 | plan, design, analysis, report |
| [dashboard-expansion](dashboard-expansion/) | Phase 4.5.9 | 97.8% | 2026-04-23 | 2026-04-23 | plan, design, analysis, report |

## dashboard-expansion — Phase 4.5.9 대시보드 확장 (매크로 지표·섹터 퍼포먼스·카테고리 분류)

대시보드를 3개 섹션으로 체계화: 주요 지수(S&P 500, Nasdaq, Dow Jones, Russell 2000) / 변동성·환율·금리(VIX, DXY, 10Y Treasury, USD/KRW) / 원자재(Gold, Silver, WTI Oil, Copper). 섹터 퍼포먼스 위젯 신규(11 GICS 섹터, FMP+Yahoo ETF fallback). Match Rate 97.8%, iteration 0회.

- **범위**: BE 6파일 (MarketOverviewService 7 매크로 심볼 확장, SectorPerformanceService FMP+Yahoo 이중 소스, MarketController /sectors 엔드포인트) + FE 6파일 (MarketOverview 3섹션 분리, SectorPerformance 바 차트, InfoTooltip 전 카드 적용) + PDCA 2파일
- **결과**: 16파일, +1,702 lines. 설계 45항목 중 39 매칭, 5 개선, 1 변경(SectorChip→SectorBar UX 개선), 0 누락
- **PR**: #31 squash-merged
- **핵심 변경**: 설계 후 사용자 요청으로 Russell 2000 추가, VIX 매크로 이동, DXY/Silver/Copper 추가, 매크로 2분류 분리
- **Key Decisions**: BE macro 단일 배열 유지 + FE에서 name 기반 카테고리 분류 (BE 변경 최소화) · DXY 심볼 DX-Y.NYB (Yahoo Finance 호환) · 3-tier fallback 재활용 (신규 코드 최소화) · Redis 15분 캐시 (섹터/매크로)
- **Lessons**: 데이터 소스 심볼은 실제 API 테스트 후 확정할 것 (DX=F → DX-Y.NYB) · FE 카테고리 분류가 BE API 구조 변경보다 유연 · InfoTooltip은 초보자 UX에 높은 가치
- **Follow-up**: 매크로 카드 클릭 → 상세 차트 페이지, 2Y Treasury 추가(안정적 데이터 소스 확보 시)

**링크**: [plan](dashboard-expansion/dashboard-expansion.plan.md) · [design](dashboard-expansion/dashboard-expansion.design.md) · [analysis](dashboard-expansion/dashboard-expansion.analysis.md) · [report](dashboard-expansion/dashboard-expansion.report.md)

## stock-detail-enrichment — Phase 4.5.8 종목 상세 기업 개요 패널 (CompanyOverviewPanel)

종목 상세 페이지에 기업 핵심 펀더멘털(섹터, 시가총액, P/E, EPS, 배당, 베타, 52주 범위) 시각화. Yahoo quoteSummary(primary) + FMP(fallback) 이중 소스, Redis 24h 캐시. Match Rate 92%, iteration 0회.

- **범위**: BE 10 파일 (CompanyOverview record, CompanyOverviewService, FmpClient.companyProfile/ratiosTtm, YahooFinanceClient.quoteSummary crumb/cookie 인증, StockController.overview, Quote week52High/Low 확장) + FE 12 파일 (CompanyOverviewPanel 8셀 그리드 + 52주 바 + 접이식 설명, InfoTooltip @floating-ui/react 공유 컴포넌트, useCompanyOverview, formatMarketCap/formatRatio/formatEmployees)
- **결과**: 22 파일, +1,568 lines. AC 7/7 (100%) 통과
- **PR**: 미생성 (feat/stock-detail-enrichment 브랜치)
- **핵심 변경**: 설계 "FMP 단일 소스" → 구현 "Yahoo primary + FMP fallback" 전환 (FMP 250 req/day 예산 절약), InfoTooltip 공유 컴포넌트 추출 (@floating-ui/react flip+shift), 6셀→8셀 정보 확대 (산업/직원수 추가)
- **Key Decisions**: Yahoo crumb/cookie 인증 (무제한, API 키 불필요) · FMP stable API 호환 (@JsonProperty 매핑) · graceful degradation (overview=null → 나머지 6블록 정상)
- **Lessons**: 데이터 소스 다중화를 처음부터 설계할 것 · CSS-only 툴팁 대신 @floating-ui 뷰포트 인식 포지셔닝 · Yahoo Finance v10 quoteSummary가 펀더멘털 정보에 최적
- **Follow-up**: 베타 피드백 수집, FMP 필드 모니터링, 에러 상태 메시지 개선, 기업 설명 한국어 번역

**링크**: [plan](stock-detail-enrichment/stock-detail-enrichment.plan.md) · [design](stock-detail-enrichment/stock-detail-enrichment.design.md) · [analysis](stock-detail-enrichment/stock-detail-enrichment.analysis.md) · [report](stock-detail-enrichment/stock-detail-enrichment.report.md)

## yahoo-migration — Phase 4.5.7 TwelveData → Yahoo Finance 마이그레이션

TwelveData 무료 플랜(8 req/min, 800 req/day) 제한 해소를 위해 Yahoo Finance v8 chart API를 1차 데이터 소스로 전환. TwelveData는 최후 fallback으로 보존. Match Rate 100%, iteration 0회.

- **범위**: BE 4 파일 (`YahooFinanceClient` +fetchIntradayCandles, `CandleService` +fetchIntradayWithFallback, `MarketOverviewService` 3-tier fallback, `TimeFrame` +yahooInterval)
- **결과**: +122/-51 lines, 설계 37/37 항목 100% 일치, `./gradlew check` BUILD SUCCESSFUL
- **PR**: #29 squash-merged (`1d13cb5`)
- **핵심 변경**: 인트라데이 캔들(Yahoo 1차→TwelveData fallback), 시장 지수/환율(Finnhub→Yahoo→TwelveData 3단 fallback)
- **Key Decisions**: TwelveData 완전 제거 아님 — 비상 fallback 보존 (사용자 명시적 요청)
- **Follow-up**: Yahoo hit rate 모니터링, `YAHOO_API_ENABLED` 런타임 토글 검토, 전용 FX API로 USDKRW Finnhub fallback 대체

**링크**: [plan](yahoo-migration/yahoo-migration.plan.md) · [design](yahoo-migration/yahoo-migration.design.md) · [report](yahoo-migration/yahoo-migration.report.md)

## ai-analysis-deepening — Phase 4.5.6 AI 참고 분석 v2 고도화 (초보자 친화 + 뉴스 신선도)

로그인 유저 전용 AI 참고 분석을 초보자 눈높이로 고도화. 프롬프트 스키마에 초보자 해설·지표 해석·뉴스 영향·관전 포인트 4 필드(옵셔널)를 추가하고, 뉴스 컨텍스트를 3→5건으로 확대 + `hours_ago` / `freshness(FRESH<24h / RECENT<72h / STALE)` 메타 주입. FE 는 섹션 분리 접이식 + grid-template-rows 애니메이션 + 뉴스 발행 일시 라벨. Match Rate 93%, iteration 0회.

- **범위**: BE 7 파일 (`AiSignal`/`IndicatorInterpretation`/`NewsImpact`/`ImpactDirection` + V16 `extended_response JSONB` + `ContextAssembler`/`ResponseValidator`/`AiSignalService` + `AiSignalAuditEntity` + 프롬프트 v2) + FE 8 파일 (타입·컴포넌트 5개·패널·`use-ai-signal` 영향 없음)
- **결과**: 23 파일, +2,153 lines. 기존 IT 생성자 시그니처 업데이트 2곳, `./gradlew check` 그린, `tsc --noEmit` / ESLint 0 errors
- **PR**: #26 squash-merged (`4fb2053`)
- **핵심 UX 개선**: 로그인 콘텐츠의 "빈약 체감" 해소 — 신호/신뢰도 외 설명형 4섹션으로 정보 밀도 강화 · 뉴스 한국어 freshness 배지 + 발행 일시(`오늘 HH:mm` / `M월 D일 (요일)` / 전체 날짜) 이중 표기 · Collapse 애니메이션 (grid-rows 0fr↔1fr + opacity fade, motion-reduce 대응) · 로딩 문구 "최대 5초" → "최소 10초" 현실화 (Gemini 8s × 재시도 2회 반영)
- **Positive Added 5건**: V16 `WHERE extended_response IS NOT NULL` 부분 인덱스 · `hoursAgo<0` 미래 시각 방어 · NewsImpact 부분 item drop 정책 (전체 응답 유지) · FreshnessBadge 한국어 라벨 · `buildExtendedResponse` 전체 null → DB NULL 저장
- **Key Decisions**: 필드 추가 (옵셔널) vs 버전 API 분기 → 추가 · JSONB 단일 컬럼 vs 컬럼 분리 → JSONB 단일 (스키마 flux) · BE ContextAssembler 에서 freshness 계산 (AI 도 활용) · 캐시 키 v1 → v2 bump (schema 불일치 차단)
- **Lessons**: 옵셔널 필드 + nullable FE 타입 + item drop 조합이 "부분 LLM 성공 수용" 패턴으로 최적 · grid-template-rows 트릭이 framer-motion 없이도 콘텐츠 높이 독립적 애니메이션 제공 · "최대 N초" 보다 "최소 N초" 가 under-promise over-deliver UX 원칙에 부합
- **Follow-up**: M-1 ResponseValidator v2 단위 테스트 / M-2 ContextAssembler freshness 경계값 단위 테스트 (93% → 96%+) · Step 10 프롬프트 dry-run (AAPL/TSLA/NVDA 실 Gemini 호출, 토큰·비용 스냅샷) · 모바일 기본 접힘 `defaultOpenMobile?` prop · timeframe 별 freshness 기준 차등 (SHORT=12h / LONG=1주일)

**링크**: [plan](ai-analysis-deepening/ai-analysis-deepening.plan.md) · [design](ai-analysis-deepening/ai-analysis-deepening.design.md) · [analysis](ai-analysis-deepening/ai-analysis-deepening.analysis.md) · [report](ai-analysis-deepening/ai-analysis-deepening.report.md)

## feedback — Phase 4.5.5 베타 피드백 채널 (/feedback + Supabase 테이블)

베타 단계 버그·문의·제안 수집 공식 창구 확보. `/feedback` 페이지에서 Supabase `public.feedback` 테이블에 직접 INSERT. RLS 로 INSERT 공개·SELECT 서비스 롤 전용. 스팸 방어 3중(허니팟 + 60s 쿨다운 + 길이 제한). 관리자 UI 미구현, Supabase Dashboard 로 갈음. Match Rate 99%, iteration 0회.

- **범위**: FE 4파일 (`app/feedback/page.tsx`, `features/feedback/feedback-form.tsx`, `features/feedback/types.ts`, `components/legal/disclaimer-footer.tsx` 수정) + Flyway V14 SQL 1파일. BE Java 0 변경
- **결과**: +1198 lines, `make web-check`/`web-build` 통과, `/feedback` Static route 등록
- **PR**: #23 squash-merged (`e1d15d3`)
- **스팸 방어 3중**: 허니팟 `name="company"` sr-only (봇 무음 차단) · 60초 쿨다운 (localStorage) · 길이 제한 (subject 1~100, body 10~2000)
- **Key Decisions**: Supabase 직접 INSERT (Spring 엔드포인트 X) · Flyway V14 (Plan "BE 변경 0" 에서 스키마 일관성 우선으로 완화) · 관리자 UI 미구현 (Dashboard 재사용) · hCaptcha 미채택 (베타 트래픽 기준 과도)
- **개선형 편차 1건**: `useState + useEffect` 동기화 → 파생 상태 `const email = user?.email ?? emailInput` 전환 — React 19 린트 규칙 `react-hooks/set-state-in-effect` 대응, 설계 의도 동일 유지
- **Lessons**: React 19 에서 `useEffect` 내 setState 가 린트 차단됨 → 파생 상태가 기본 패턴 · Flyway 단일 소스가 Supabase native 마이그레이션 분리보다 스키마 일관성 유리 · 베타 단계에서 관리자 UI 는 Dashboard 로 충분, 개발 리소스 절약
- **Follow-up**: hCaptcha / Turnstile 통합 (트래픽 증가 시) · 확인 메일 자동 발송 · Slack/Discord 웹훅 실시간 알림 · 첨부 파일 업로드 (Supabase Storage)

**링크**: [plan](feedback/feedback.plan.md) · [design](feedback/feedback.design.md) · [analysis](feedback/feedback.analysis.md) · [report](feedback/feedback.report.md)

## password-reset — Phase 4.5.4 비밀번호 재설정 플로우 (FE 전용, BE 무변경)

이메일/비밀번호 가입자의 계정 복구 경로 완성. Supabase Auth 표준 PKCE 플로우(`resetPasswordForEmail` + `updateUser`)를 FE 2개 페이지 + 2개 폼으로 구현. 기존 `/auth/callback` 의 `next` 쿼리 재사용으로 백엔드 변경 0. Match Rate 99%, iteration 0회.

- **범위**: FE 5개 파일 (`app/auth/forgot-password/page.tsx` + `features/auth/forgot-password-form.tsx` + `app/auth/reset-password/page.tsx` + `features/auth/reset-password-form.tsx` + `app/auth/login/page.tsx` 링크 추가), BE 0파일
- **결과**: +1214 lines (docs 포함), `make web-check` 0 errors, 새 route 2개 Static 등록. FR 9/10 (FR-10 "이미 로그인된 사용자 리다이렉트"는 Plan/Design 에서 선택 생략)
- **PR**: #22 squash-merged (`3511481`)
- **상태 머신**: forgot `idle → loading → sent / error`, reset `checking → invalid | ready → loading → error / 성공(홈 이동)`
- **보안**: 이메일 존재 여부 비공개(enumeration 방지), `getUser()` 세션 가드, PKCE/CSRF/rate-limit 는 Supabase SDK 내장
- **에러 매핑**: `mapErrorMessage` 헬퍼로 rate limit / invalid email / 6자 미만 / old password 동일 / invalid·expired·jwt 5케이스 한국어 매핑 — Design §4.1 에러 테이블의 런타임 실체화
- **불변 영역**: `/auth/callback/route.ts`, `login-form.tsx`, `signup-form.tsx`, `auth-provider.tsx`, `@/lib/supabase/*` 전부 미변경
- **Lessons**: 기존 `/auth/callback` 의 `next` 쿼리 처리가 이미 존재하여 BE/route 재사용만으로 완결. Supabase Auth SDK 가 PKCE/CSRF/rate-limit 을 내장 처리해 FE 로직은 호출·상태머신·에러 매핑에만 집중 가능

**링크**: [plan](password-reset/password-reset.plan.md) · [design](password-reset/password-reset.design.md) · [analysis](password-reset/password-reset.analysis.md) · [report](password-reset/password-reset.report.md)

## notification-news — Phase 4.5.3 뉴스 알림 부활 (watermark 기반 dedup)

Phase 4.5.2 에서 보존한 `onNewNews` 토글을 실제 동작하는 뉴스 알림으로 부활. `last_news_published_at` watermark 기반 3-way 정책(BASELINE/SEND/NOOP)으로 이산 이벤트(뉴스 기사) 중복 억제, 첫 사이클 baseline fail-safe, publishedAt null 방어, `/stocks/{ticker}` 딥링크 지원. Match Rate 100%, iteration 0회.

- **범위**: Flyway V12(`ALTER TABLE ... ADD COLUMN IF NOT EXISTS`) + `NotificationSettingEntity.lastNewsPublishedAt` + `markNewsNotified()` + 순수 함수 `NotificationNewsDedupPolicy`(3 Action, decide 메서드) + `NotificationCheckService.checkNewNews()` + `PushService.sendToUser(4-arg)` 오버로드 + FR-10 가격 알림 딥링크 추가
- **결과**: +1018/-12, 신규 테스트 13건(N1~N6 + U6~U7 + P1~P5). `./gradlew check` 38초 BUILD SUCCESSFUL, 기존 T1~T9 / U1~U5 회귀 0
- **PR**: #14 squash-merged (`5a87e21`)
- **핵심 개선**: 뉴스 5건 중 newer 다건 시 target=최신 + `newerCount` 에 "외 N-1건" 합성 · watermark=null 첫 사이클 발송 대신 최신 시점 BASELINE 으로 전진 · publishedAt null 필터링 후 모두 null 이면 NOOP · sw.js 기존 `data.url` 처리 로직 재사용 → **FE 변경 0건**
- **Key Decisions**: watermark vs hysteresis (이산 이벤트는 watermark 가 더 자연스러움) · 순수 함수 분리(I/O 없음, 테스트 용이) · PushService 3-arg 오버로드 유지(레거시 호환) · Report 수준 범위 확장(FR-10 가격 알림 딥링크, Design 외 추가)
- **Follow-up**: `newerCount` 를 body 대신 badge 로 표현(다국어 대응) · 종목별 watermark 지원 시 `notification_settings` 정규화 검토

**링크**: [plan](notification-news/notification-news.plan.md) · [design](notification-news/notification-news.design.md) · [analysis](notification-news/notification-news.analysis.md) · [report](notification-news/notification-news.report.md)

## notification-ui-cleanup — Phase 4.5.2 아이콘 전용 버튼 + 활성 상태 + 죽은 토글 제거

종목 상세 북마크/알림 버튼을 아이콘 전용으로 정리, 알림 활성 상태를 색(blue)으로 피드백. 죽은 토글 `onSignalChange`를 FE 타입 → 모달/섹션/리스트 UI → BE DTO/Entity/Service/EntityTest → DB(Flyway V11 DROP) 풀 스택에서 완전 제거. Match Rate 100%, iteration 0회, net -8 lines (순 감소).

- **범위**: FE 6 파일(bookmark-button, notification-button, notification-setting-modal, notification-settings, my-page/notification-section, types/notification) + BE 5 파일(Entity/Request/Response/Service/EntityTest) + Flyway V11 DROP COLUMN
- **결과**: 12 파일, +40/-48 (net -8). 기존 테스트 회귀 0 (Entity U1~U5 3-arg 전환, DedupPolicy T1~T9 불변)
- **PR**: #13 squash-merged (`{예정}`)
- **핵심 UX 개선**: 아이콘 전용 + hover tooltip (title 속성) · 북마크(노랑) vs 알림(파랑) 색 구분 · 활성 시 종 fill="currentColor" · 죽은 옵션 제거로 모달 노이즈 감소
- **YAGNI 판단**: AI 시그널 알림 구현 시 LLM 배치 호출(~600+/day) 토큰 비용이 "참고용 분석 도구" 포지셔닝 대비 과함 → 구현 대신 **삭제** 선택. 재도입 시 `notification-signal` 신규 feature 로
- **보존**: `onNewNews` 는 유지 (추후 `notification-news` feature 에서 RSS 기반 구현 예정), `NotificationCheckService`/`NotificationDedupPolicy`/`PushService` 불변

**링크**: [plan](notification-ui-cleanup/notification-ui-cleanup.plan.md) · [design](notification-ui-cleanup/notification-ui-cleanup.design.md) · [analysis](notification-ui-cleanup/notification-ui-cleanup.analysis.md) · [report](notification-ui-cleanup/notification-ui-cleanup.report.md)

## notification-dedup — Phase 4.5.1 Web Push 중복 억제 (히스테리시스 + 쿨다운)

15분 주기 스케줄러가 임계값 초과 상태에서 매 사이클 동일 알림을 발송하던 스팸 버그를 제거. 상태 전이 게이트 + 히스테리시스(리셋=임계×0.6) + 4h 쿨다운 + 푸시 성공 시에만 상태 전진하는 fail-safe. Match Rate 100%, iteration 0회.

- **범위**: `NotificationDedupPolicy` 순수 함수(5 Action) + `NotificationDedupProperties`(`app.notification.dedup.*`) + `NotificationSettingEntity` 필드 2개(`lastNotifiedAt`, `lastTriggeredAbove`) + Flyway V10 + `PushService.sendToUser` void→boolean + `NotificationCheckService` 리팩터
- **결과**: 14 unit tests (Policy 9 + Entity 5), `./gradlew check` BUILD SUCCESSFUL, 빌드 경고 0
- **PR**: #12 squash-merged (`{예정}`)
- **핵심 UX 개선**: 첫 돌파 1회만 발송 · 경계 진동(4.9%↔5.1%) 차단 · 리셋 후 재돌파도 4h 쿨다운 · 시계 역전 fail-safe(SKIP_COOLDOWN)
- **Non-gap 조정 5건**: V8→V10(슬롯 점유), `NotificationCheckServiceIntegrationTest` 의도적 연기(Design optional), 나머지 3건은 인프라 재사용/Design 정정 반영

**링크**: [plan](notification-dedup/notification-dedup.plan.md) · [design](notification-dedup/notification-dedup.design.md) · [analysis](notification-dedup/notification-dedup.analysis.md) · [report](notification-dedup/notification-dedup.report.md)

## phase4.5-improvements — Phase 4.5 캔들 DB + 마이페이지 + 알림 UX + Rate Limiter

캔들 DB 레이어(Yahoo Finance on-demand + 일간 배치) + 마이페이지 4섹션 리디자인 + 종목 상세 알림 버튼 + Rate Limiter(Token Bucket) + 회원 탈퇴(soft delete, 2년 보관) + 잔여 Gap 8건 해소. Match Rate 96.4%, iteration 0회.

- **범위**: BE 캔들 인프라(Flyway V8/V9, YahooFinanceClient, CandleService, CandleBatchScheduler) + FE 마이페이지(6 컴포넌트) + 알림 설정 모달 + Rate Limiter + 계정 삭제/복구 + 법적 문서 업데이트
- **결과**: 25 commits, 63 파일, +3,246 lines. 설계 14 steps 중 13 fully implemented, 1 partial→fixed
- **PR**: #11 squash-merged (`754a01b`)
- **설계 외 추가 구현**: 회원 탈퇴(soft delete + Supabase ban), 재가입 시 계정 복구(unban + restore), 이용약관 9조 확장, 개인정보 처리방침 현행화, cursor-pointer 전수 적용(17 FE 파일)

**링크**: [plan](phase4.5-improvements/phase4.5-improvements.plan.md) · [design](phase4.5-improvements/phase4.5-improvements.design.md) · [analysis](phase4.5-improvements/phase4.5-improvements.analysis.md) · [report](phase4.5-improvements/phase4.5-improvements.report.md)

## auth — Phase 4 인증 / 북마크 / Web Push 알림

Supabase Auth + Spring Security JWT 기반 인증, 북마크 CRUD, Web Push 알림. Match Rate 95%, iteration 0회.

- **범위**: Phase 4.0 인증 기반 (Google OAuth + 이메일, ES256 JWKS JWT 검증, two-chain SecurityFilterChain) + Phase 4.1 북마크 CRUD (BE + FE + 마이페이지) + Phase 4.2 Web Push (VAPID, Service Worker, 15분 스케줄러)
- **결과**: 78 파일, +4,369 lines. BE 29/29, FE 27/27, DB 마이그레이션 100% 일치, 규칙 준수 97%
- **PR**: #10 squash-merged (`88f64ee`)
- **의도적 개선 6건**: two-chain Security, ES256+RS256, route.ts callback, VAPID API endpoint, AuthGuardModal, BigDecimal price
- **버그 수정 7건**: 401 on all APIs, ddl-auto data loss, Supabase bulk insert timeout, CORS protected chain, ES256 algorithm mismatch, API path prefix duplication, bookmark link path

**링크**: [plan](auth/auth.plan.md) · [design](auth/auth.design.md) · [analysis](auth/auth.analysis.md) · [report](auth/auth.report.md)

## market-dashboard — Phase 3 시장 대시보드

메인 페이지를 시장 대시보드로 전환. 지수·VIX·환율·시장 뉴스·급등락 종목을 한 화면에 제공. Match Rate 95%, iteration 0회.

- **범위**: BE 3개 엔드포인트 (`/market/overview`, `/market/news`, `/market/movers`) + FE 대시보드 위젯 3종 + 메인 페이지 통합
- **결과**: 25 파일, +2,213 lines. Finnhub→TwelveData fallback, 인기 종목 30개 풀 기반 movers, NewsTranslator 재사용, Redis 캐시 (5분/15분)
- **PR**: #9 squash-merged (`78ec523`)
- **Known gaps**: `MarketMover.volume` 필드 미포함, `usdKrwChange` 환율 변동 미계산 (둘 다 Low impact, Phase 3.1 개선 가능)

**링크**: [plan](market-dashboard/market-dashboard.plan.md) · [design](market-dashboard/market-dashboard.design.md) · [report](market-dashboard/market-dashboard.report.md)

## phase2.2-prompt-externalization — Phase 2.2 프롬프트 외부화 + Gemini 재시도 1회 루프

Phase 2 archive index 의 deferred 2건(프롬프트 외부화 / Gemini 재시도 1회)을 단일 PDCA 사이클로 동시 해소. Match Rate 96% (24/25, 1 partial — 회귀 byte-equality 테스트 부재는 옵셔널), 신규 테스트 7건 (L-1~L-3 + R-1~R-4) green.

- **범위**: `PromptLoader` + `classpath:prompts/{ai-signal,news-translate}.system.txt` 외부화, `GeminiLlmClient` transient(5xx/429/timeout) 1회 재시도(MAX_ATTEMPTS=2, 250ms 고정 backoff), `llm.retry.count{outcome=success|exhausted}` 메트릭 신설
- **결과**: production 코드 net +260, 테스트 +247 (R-1~R-4 = 186줄, L-1~L-3 = 60줄). Phase 2.1 메트릭 14개 시리즈 회귀 0, runtime `/actuator/prometheus` 노출 검증 완료
- **PR**: #8 squash-merged (`9344b4b`)
- **Lessons**: RetryableException marker 패턴이 generate/callOnce 분리 + transient 분류 매트릭스 표현에 가장 명료, MockWebServer enqueue 개수와 retry MAX_ATTEMPTS 정합으로 retry 행위 fully verifiable, ConcurrentHashMap.computeIfAbsent + ResourceLoader 가 ForbiddenTermsRegistry 패턴과 정확히 대칭
- **Design 보완**: §4.2/§10.2 retry WARN 로그 포맷이 실제 구현(success INFO + exhausted WARN 두 지점)과 미세 차이 — `retry.count` 메트릭으로 의도 충족하므로 후속 design 정정 권고

**링크**: [plan](phase2.2-prompt-externalization/phase2.2-prompt-externalization.plan.md) · [design](phase2.2-prompt-externalization/phase2.2-prompt-externalization.design.md) · [analysis](phase2.2-prompt-externalization/phase2.2-prompt-externalization.analysis.md) · [report](phase2.2-prompt-externalization/phase2.2-prompt-externalization.report.md)

## phase2.1-metrics-fe-refactor — Phase 2.1 Micrometer 관측성 + FE stock-detail 재배치

Phase 2 잔여 Major gap 2건(FR-15 Micrometer 미구현 + FE 폴더 편차)을 단일 스코프로 해소. Match Rate 88% → 100% (pdca-iterator 1 iteration 으로 3 Gap 전부 해소), 33/33 tests green.

- **범위**: Micrometer 5 메트릭 (`llm.call.count` / `llm.token.total` / `llm.failure.count` / `llm.forbidden.hit.count` / `llm.call.latency` Timer) + `/actuator/prometheus` 노출 + FE `features/stock-detail/{news,ai-signal}/` 재배치
- **결과**: 요구사항 25/25 (100%), Acceptance §9 8/8, tag allowlist 6종 (feature/model/direction/reason/layer/outcome — ticker 배제), 이론 최대 22 시계열
- **PR**: #3 squash-merged (`40fad50`)
- **Lessons**: CI forbidden-terms scan 범위를 production source 로 한정 (테스트 픽스처의 의도적 forbidden 용어 허용), MockWebServer 로 WebClient 외부 호출도 자동 단위 검증, MeterBinder 기동 시점 register 로 Actuator smoke 통과 보장

**링크**: [plan](phase2.1-metrics-fe-refactor/phase2.1-metrics-fe-refactor.plan.md) · [design](phase2.1-metrics-fe-refactor/phase2.1-metrics-fe-refactor.design.md) · [analysis](phase2.1-metrics-fe-refactor/phase2.1-metrics-fe-refactor.analysis.md) · [report](phase2.1-metrics-fe-refactor/phase2.1-metrics-fe-refactor.report.md)

## phase2-rag-pipeline — Phase 2 RAG 파이프라인 (뉴스 + AI 시그널)

Phase 1 "보여주기" 위에 해석 레이어 추가. RAG 4단계 (ContextAssembler → PromptBuilder → LlmClient → ResponseValidator) + 뉴스 한국어 요약 + 4-level 금지용어 가드 + `/detail` hydrate.

- **범위**: `/stocks/{t}/news`, `/stocks/{t}/ai-signal` 2개 신규 엔드포인트 + Flyway V3/V4 + NewsPanel/AiSignalPanel
- **결과**: Match Rate 93% (85% → +8pt, iteration 1회), Launch Gate 4/4 통과 (레드팀 20/20, 금지용어 CI, 면책 100%), 4-level guard 완성 (constants → prompt → validator → LegalGuardFilter servlet)
- **PR**: #1 squash-merged (`16dfaa7`)
- **후속 (Phase 2.1/3)**: FR-15 Micrometer counter, FE 폴더 `features/stock-detail/{news,ai-signal}/` 재배치, 프롬프트 `resources/prompts/*.txt` 외부화, 재시도 1회 루프

**링크**: [prd](phase2-rag-pipeline/phase2-rag-pipeline.prd.md) · [plan](phase2-rag-pipeline/phase2-rag-pipeline.plan.md) · [design](phase2-rag-pipeline/phase2-rag-pipeline.design.md) · [analysis](phase2-rag-pipeline/phase2-rag-pipeline.analysis.md) · [report](phase2-rag-pipeline/phase2-rag-pipeline.report.md)

## mvp — AI Stock Advisor Phase 1 MVP

한국어 UI 기반 미국 주식 종목 상세 분석 대시보드 구현. Finnhub + Twelve Data hybrid 로 무료 플랜 내 MVP 완주.

- **범위**: 검색 / 프로파일 / 시세 / 캔들 / 기술 지표 (5 Phase 1 엔드포인트 + `/detail` scaffold)
- **결과**: 구현 항목 40/42 일치, Gap Critical 0 / Major 2 (결정 대기) / Minor 4, Design Drift 5건 동기화 완료
- **Phase 2/4 Deferred**: 뉴스/AI 시그널/인증/북마크 — 설계대로 보류
- **후속**: `SearchHit.exchange` nullable, `Quote.volume` 소스 결정, `/detail` 통합 테스트, Phase 2 착수 준비

**링크**: [prd](mvp/mvp.prd.md) · [plan](mvp/mvp.plan.md) · [design](mvp/mvp.design.md) · [do](mvp/mvp.do.md) · [analysis](mvp/mvp.analysis.md) · [report](mvp/mvp.report.md)
