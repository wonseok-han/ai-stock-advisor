# Archive — 2026-04

완료된 PDCA 사이클 문서 보관소.

| Feature | Phase | Match Rate | Started | Archived | 문서 |
|---|---|---:|---|---|---|
| [mvp](mvp/) | Phase 1 | 94% | 2026-04-10 | 2026-04-14 | prd, plan, design, do, analysis, report |
| [phase2-rag-pipeline](phase2-rag-pipeline/) | Phase 2 | 93% | 2026-04-14 | 2026-04-14 | prd, plan, design, analysis, report |

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
