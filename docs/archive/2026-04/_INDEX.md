# Archive — 2026-04

완료된 PDCA 사이클 문서 보관소.

| Feature | Phase | Match Rate | Started | Archived | 문서 |
|---|---|---:|---|---|---|
| [mvp](mvp/) | Phase 1 | 94% | 2026-04-10 | 2026-04-14 | prd, plan, design, do, analysis, report |
| [phase2-rag-pipeline](phase2-rag-pipeline/) | Phase 2 | 93% | 2026-04-14 | 2026-04-14 | prd, plan, design, analysis, report |
| [phase2.1-metrics-fe-refactor](phase2.1-metrics-fe-refactor/) | Phase 2.1 | 100% | 2026-04-14 | 2026-04-14 | plan, design, analysis, report |
| [phase2.2-prompt-externalization](phase2.2-prompt-externalization/) | Phase 2.2 | 96% | 2026-04-15 | 2026-04-15 | plan, design, analysis, report |

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
