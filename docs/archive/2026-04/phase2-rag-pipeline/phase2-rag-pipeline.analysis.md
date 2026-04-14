# Phase 2 RAG Pipeline — Gap Analysis

> **Feature**: phase2-rag-pipeline · **Date**: 2026-04-14 · **Commit**: 8085f81 · **Analyzer**: bkit:gap-detector (Act-1 재검증)

## Executive Summary

**Overall Match Rate: 93%** — Launch Gate 4/4 통과. iteration 불필요, Report 단계 진입 가능.

| Category | Match | Status |
|---|:-:|:-:|
| FR-01 ~ FR-20 구현 | 95% (19/20) | 양호 |
| Design §3~§7 핵심 구조 | 95% | 양호 |
| Launch Gate 기준 | 4/4 충족 | 통과 |
| 면책 100% 부착 | 100% | 통과 |
| 금지용어 CI | 동작함 | 통과 |
| 레드팀 20/20 | 자동 테스트 통과 | 통과 |

## Launch Gate 충족 (4/4)

| 기준 | 상태 | 증빙 |
|---|:-:|---|
| Match Rate ≥ 90% | ✅ (93%) | Critical 3건 해소 |
| 레드팀 20/20 통과 | ✅ | `RedTeamPromptInjectionTest` @ParameterizedTest 20 케이스 all rejected (`build/test-results/test/TEST-*.xml` tests=20 failures=0) |
| 금지용어 CI | ✅ | `.github/workflows/forbidden-terms.yml` |
| 면책 100% 부착 | ✅ | `Disclaimers` + LegalGuardFilter neutral rewrite |

## Critical Gaps (해소됨 ✅)

1. **FR-14 LegalGuardFilter** — `apps/api/src/main/java/com/aistockadvisor/legal/LegalGuardFilter.java` 신규. OncePerRequestFilter + ContentCachingResponseWrapper 로 `/api/v1/**` JSON 응답 body 스캔 → `/ai-signal` 은 AiSignal-shape neutral (NEUTRAL/0.5/MID/fallback=true), `/news` 는 `[]`, 기타는 generic neutral 로 치환. 4-level guard 완성.
2. **FR-20 RedTeamPromptInjectionTest** — ko 금지용어 11 + 스키마 위반 7 + 프롬프트 주입 2 = 20 케이스 전부 `result.valid()==false` 검증. CSV 구분자 `::` 로 JSON payload 안전 주입. 20/20 pass.
3. **API 경로 계약** — Design §4.1/§4.2 와 일치: `/api/v1/stocks/{ticker}/news?limit=`, `/api/v1/stocks/{ticker}/ai-signal?tf=`. FE `lib/api/news.ts`·`lib/api/ai-signal.ts` 동일 경로로 정렬. Phase 1 컨벤션 통일.

## 남은 Gap (비차단)

### Major (Phase 2 후속 보강 또는 Phase 3 이관)

4. **FR-15 Micrometer counter 미구현** — Design §13.2 Step 20, v0.2-rc3 gate. LLM 호출/토큰/실패/금지용어 counter 없음. 로깅(`log.warn`) 은 존재.
5. **FE 폴더 편차** — 구현 `features/news/`, `features/ai-signal/` vs Design §5.3 `features/stock-detail/{news,ai-signal}/`. 기능 동일, 폴더 재배치 시 95%+ 달성.

### Minor

6. **프롬프트 Java 인라인** — Design §6.1/§12.2 `resources/prompts/*.txt` 외부화 미이행. §13.3 에 대체 결정 기록됨 (합의된 편차).
7. **rationale 개수** — Design §6.2 "3~5개" vs 구현 "2~4개" (프롬프트 제약).
8. **fallback rationale/risks** — Design §4.3 빈 배열 vs 구현 안내 문구 2개 (UX 개선).
9. **V4 idx_ai_signal_audit_fallback** — 인덱스 미포함.
10. **re-try 1회 로직** — Design §7.3/§8.3 명시, ResponseValidator 루프 미구현.
11. **토큰 8K 가드** — `maxOutputTokens=1024` 만 설정, 입력 clipping 없음.

### Resolved in §13.3 (합의된 대체)

- Bucket4j → Redis `INCR` 분-버킷 (`AiSignalRateLimiter`)
- Resilience4j circuit breaker → try/catch + neutral fallback + Audit
- jmustache → Java 텍스트 빌드 + 경계 마커
- WireMock → ResponseValidator 단위 20-케이스 레드팀 테스트

## Recommended Actions

1. **[Report]** `/pdca report phase2-rag-pipeline` 진행
2. **[Phase 2.1]** FR-15 Micrometer counter 보강
3. **[Phase 2.1]** FE 폴더 `features/stock-detail/{news,ai-signal}/` 로 재배치
4. **[Phase 3]** 프롬프트 파일 외부화, 재시도 루프, 토큰 clipping

## Conclusion

Launch Gate 통과 → **Report 단계로 진행**. Major 2건은 Phase 2.1 또는 Phase 3 범위로 이관.
