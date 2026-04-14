# Phase 2 RAG Pipeline — Gap Analysis

> **Feature**: phase2-rag-pipeline · **Date**: 2026-04-14 · **Commit**: d6f6ee0 · **Analyzer**: bkit:gap-detector

## Executive Summary

**Overall Match Rate: 85%** — Launch Gate 90% 미달. Critical 3건 해소 시 92%+ 예상, 1회 iteration 권장.

| Category | Match | Status |
|---|:-:|:-:|
| FR-01 ~ FR-20 구현 | 80% (16/20) | 주의 |
| Design §3~§7 핵심 구조 | 90% | 양호 |
| Launch Gate 기준 | 3/4 충족 | 미달 |
| 면책 100% 부착 | 100% | 통과 |
| 금지용어 CI | 동작함 | 통과 |
| 레드팀 20/20 | 차단 로직 존재, 테스트 미구현 | 미달 |

## Critical Gaps (Launch Gate 차단)

1. **FR-14 LegalGuardFilter (Servlet) 미구현** — Level 3 우회 시 최종 방어막 부재. 4-level 가드가 3-level 로 축소됨.
2. **FR-20 Red team 20 케이스 통합 테스트 미구현** — `apps/api/src/test` 에 ApiApplicationTests 만 존재. Launch Gate "레드팀 20/20 통과" 자동검증 불가 (단, prompt 경계 마커·ticker regex·ResponseValidator 로 런타임 차단은 존재).
3. **API 엔드포인트 경로 계약 편차** — Design §4: `/api/v1/stocks/{ticker}/news`, `/stocks/{ticker}/ai-signal`. 구현: `/api/v1/news?ticker=`, `/api/v1/ai-signal?ticker=&tf=` (query param). Phase 1 `/stocks/{ticker}/{resource}` 컨벤션과 불일치.

## Major Gaps

4. **FR-15 Micrometer metrics 미구현** — LLM 호출/토큰/실패/금지용어 counter 없음.
5. **FR-17 Bucket4j 미도입** — AiSignalRateLimiter (Redis INCR) 대체. 기능 동등, Gradle deps 편차.
6. **FR-19 Resilience4j circuit breaker 미도입** — try/catch fallback 대체. 연속 실패 차단 없음.
7. **프롬프트 파일 분리 미이행** — Design §6.1/§12.2 `resources/prompts/*.txt`. 구현: Java 인라인.
8. **FE 폴더 구조 편차** — Design §5.3: `features/stock-detail/news/`. 구현: `features/news/`.

## Minor Gaps

9. **rationale 개수**: Design §6.2 "3~5개" vs 구현 "2~4개".
10. **fallback rationale/risks**: Design §4.3 빈 배열 vs 구현 안내 문구 2개 (UX 개선).
11. **V4 idx_ai_signal_audit_forbidden**: GIN 인덱스 (Design: BTREE). 성능상 개선.
12. **idx_ai_signal_audit_fallback 누락** — V4 SQL 에 인덱스 미포함.
13. **re-try 1회 로직 누락** — Design §7.3/§8.3 명시.
14. **토큰 제한 8K 가드** — `maxOutputTokens=1024` 만 설정, 입력 clipping 없음.

## Launch Gate 평가

| 기준 | 충족 | 비고 |
|---|:-:|---|
| Match Rate ≥ 90% | ❌ (85%) | Critical 3건 해소 시 충족 |
| 레드팀 20/20 통과 | ⚠️ 부분 | 런타임 O · 자동 테스트 X |
| 금지용어 CI | ✅ | `.github/workflows/forbidden-terms.yml` |
| 면책 100% 부착 | ✅ | Disclaimers × FE 3곳 |

## Recommended Actions

1. **[Critical]** LegalGuardFilter (OncePerRequestFilter) 구현
2. **[Critical]** RedTeamPromptInjectionTest 20 케이스 픽스처
3. **[Critical]** API 경로 `/stocks/{ticker}/news`·`/ai-signal` 로 통일
4. **[Major]** Micrometer Counter/Timer 주입
5. **[Major]** ResponseValidator 재시도 1회 루프
6. **[Minor]** 프롬프트 `resources/prompts/*.txt` 외부화
7. **[Docs]** Design §13.3 bucket4j/resilience4j/jmustache/wiremock 대체 결정 기록

## Iteration Target

Critical 3건 자동 수정 후 재검증 → Match Rate 92%+ · Launch Gate 통과 목표.
