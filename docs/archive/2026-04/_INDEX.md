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
