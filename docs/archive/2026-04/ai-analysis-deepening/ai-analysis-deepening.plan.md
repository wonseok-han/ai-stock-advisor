# ai-analysis-deepening Planning Document

> **Summary**: 로그인 유저 전용 "AI 참고 분석" 콘텐츠를 초보자 눈높이로 재설계. 프롬프트 스키마 확장 + 뉴스 신선도 보장 + 용어 해설 섹션 신설.
>
> **Project**: AI Stock Advisor
> **Version**: v0.1.0 Beta
> **Author**: wonseok-han
> **Date**: 2026-04-21
> **Status**: Draft

---

## Executive Summary

| Perspective | Content |
|-------------|---------|
| **Problem** | 현재 AI 참고 분석이 로그인 장벽까지 설정한 "메인 가치" 콘텐츠인데, 실제 출력은 `rationale` 2-4개 단문 + 1문단 `summary` 에 그쳐 초보 투자자가 "이 신호가 왜 나왔고 내가 뭘 알아야 하는지" 이해하기 어렵습니다. 뉴스 반영도 3건 제한 + freshness 검증 부재로 체감 상 "오래된 뉴스 같다" 가 종종 발생합니다. |
| **Solution** | (1) 프롬프트 스키마를 초보자 친화 필드 중심으로 확장 (`beginner_explanation` / `indicator_interpretation` / `news_impact` / `what_to_watch`), (2) ContextAssembler 에 뉴스 freshness 보정 + 건수 확대(3→5) + 발행시각 신호 주입, (3) FE 렌더를 섹션 단위로 분리해 접이식/탭 구조로 확장. |
| **Function/UX Effect** | 로그인 유저는 "시그널/신뢰도" 외에 "왜 이런 지표인지(RSI 67은 과매수 근접 의미)", "이 뉴스가 가격에 어떤 영향일 수 있는지", "앞으로 뭘 관찰해야 하는지" 를 한 화면에서 체감. 뉴스는 "24시간 내 N건" 표시로 신선도 시각화. |
| **Core Value** | **"초보자도 읽히는 AI 참고 분석"** — 로그인 가치 명확화. signal-accuracy(정합도) 가 사후 검증이라면 본 기능은 사전 설명력 강화로 쌍을 이룹니다. |

---

## 1. Overview

### 1.1 Purpose

로그인 후에만 접근 가능한 AI 참고 분석을 "초보 투자자가 혼자 읽어도 판단 재료를 얻을 수 있는 수준"으로 고도화합니다. 프롬프트 스키마·컨텍스트 조립·FE 렌더 세 레이어를 함께 수정해 정보량과 가독성을 동시에 끌어올립니다.

### 1.2 Background

- **현재 AI 출력**(`prompts/ai-signal.system.txt`, 6 필드): `signal / confidence / timeframe / rationale[2-4] / risks[2-4] / summary_ko`. rationale 은 "짧은 문장" 제약으로 용어 해설 공간이 없음.
- **현재 컨텍스트**(`ContextAssembler`): profile / quote / indicators(MACD·BB·RSI·MA) / recent_news[3]. 뉴스는 `NewsService.getNews(ticker, 3)` — DEFAULT_LIMIT 5 이므로 3건만 사용 중. 신선도 표기 부재.
- **현재 FE**(`ai-signal-panel.tsx`): 시그널 히어로 + summary 1 문단 + rationale/risks 리스트 + 면책. 초보자용 설명 블록 없음.
- **사용자 피드백**: "로그인 콘텐츠인데 너무 빈약" — 로그인 장벽 대비 체감 가치가 낮아 이탈 위험.

### 1.3 Related Documents

- 상위 기획: `docs/planning/05-ai-strategy.md`
- 페어 기능: `docs/archive/.../signal-accuracy.*` (사후 정합도 측정, PR #25)
- 현 프롬프트: `apps/api/src/main/resources/prompts/ai-signal.system.txt`
- 현 컨텍스트: `apps/api/src/main/java/com/aistockadvisor/ai/service/ContextAssembler.java`
- 현 FE: `apps/web/src/features/stock-detail/ai-signal/ai-signal-panel.tsx`

---

## 2. Scope

### 2.1 In Scope

- [ ] 프롬프트 시스템 템플릿 확장 (`ai-signal.system.txt`): 출력 JSON 스키마에 초보자 친화 필드 추가
- [ ] 프롬프트 유저 템플릿 확장: 컨텍스트에 `news_freshness` 블록 추가, timeframe 별 질문 가이드 포함
- [ ] `AiSignalResponse` DTO / 엔티티 확장 (신규 필드 파싱·저장)
- [ ] `ContextAssembler`: 뉴스 건수 3 → 5 로 확장, 발행시각 기반 "최신성" 메타 삽입 (예: `hours_ago`, `freshness_tier`)
- [ ] `NewsService` 보강 필요 여부 점검 (24h 내 건수 확보 전략 · lookback 재검토)
- [ ] FE 렌더 재설계 (`ai-signal-panel.tsx` + 신규 서브 컴포넌트): 섹션 단위 (핵심 신호 / 초보자용 해설 / 지표 해석 / 뉴스 영향 / 관찰 포인트 / 리스크)
- [ ] 기존 `rationale` / `risks` 는 FE 상 호환 유지 (스키마 전환기용)
- [ ] LegalGuardFilter 호환 — 신규 필드도 스캔 대상
- [ ] DB 마이그레이션: `ai_signal_audit` 테이블에 신규 필드 JSONB 컬럼 1개 추가 (단건 확장 vs 각 필드 컬럼화 여부 Design 에서 결정)
- [ ] 프롬프트 토큰 증가 영향 평가 (Gemini 2.5 Flash 비용·지연)

### 2.2 Out of Scope

- 종목 추천 / 매수·매도 직접 지시 (법적 원칙)
- 실시간 스트리밍 분석 (현재 on-demand 요청 기반 유지)
- AI 모델 교체 (Gemini 2.5 Flash 유지)
- 뉴스 요약 재번역 로직 (번역 품질은 기존 NewsTranslator 유지)
- 포트폴리오 관점 분석 (단일 종목 범위)
- 비로그인 유저 노출 확대 (login-gated 유지 — signup 유도 관점은 본 plan 에서 다루지 않음)
- signal-accuracy 테이블 스키마 변경 (독립)

---

## 3. Requirements

### 3.1 Functional Requirements

| ID | Requirement | Priority | Status |
|----|-------------|:--------:|:------:|
| FR-01 | AI 응답 JSON 스키마에 `beginner_explanation` (용어·맥락 해설, 2-4 문장 Korean) 필드 추가 | High | Pending |
| FR-02 | AI 응답에 `indicator_interpretation` 배열 추가 — 각 항목 `{indicator, value, meaning_ko}` 구조로 MACD/BB/RSI/MA 각각 초보자 설명 | High | Pending |
| FR-03 | AI 응답에 `news_impact` 배열 추가 — 최근 뉴스별 `{title_ko, impact_direction: POSITIVE\|NEGATIVE\|NEUTRAL, impact_reason_ko}` | High | Pending |
| FR-04 | AI 응답에 `what_to_watch` 배열 추가 — 초보자가 앞으로 관찰할 포인트 2-3개 (한국어 단문) | High | Pending |
| FR-05 | ContextAssembler 뉴스 3건 → 5건으로 확장, 각 뉴스에 `hours_ago` / `freshness_tier` (FRESH <24h / RECENT <72h / STALE) 메타 포함 | High | Pending |
| FR-06 | FE `ai-signal-panel` 을 섹션 분리 렌더로 리팩터 (기본 펼침: 신호/해설, 접힘: 지표 해석/뉴스/관찰 포인트) | High | Pending |
| FR-07 | 뉴스 섹션에 발행 시각 상대 표기 ("3시간 전") 추가 | Medium | Pending |
| FR-08 | 레거시 `rationale` / `risks` 유지 — 스키마 전환 간 FE 안전망 | Medium | Pending |
| FR-09 | 프롬프트 신규 출력 전부 금지어 필터 통과 (LegalGuardFilter) | High | Pending |
| FR-10 | DB `ai_signal_audit` 에 확장 필드 저장 (단일 JSONB or 컬럼 별 — Design) | High | Pending |
| FR-11 | 빈약 응답 (`indicator_interpretation=[]` 등) 감지 시 FE 는 기존 minimal UI 로 graceful degrade | Medium | Pending |
| FR-12 | 초보자 해설에 용어 글로서리 링크 or tooltip (RSI / MACD / Bollinger / MA) — 설명 반복 비용 절감 | Low | Pending |

### 3.2 Non-Functional Requirements

| Category | Criteria | Measurement Method |
|----------|----------|-------------------|
| Performance | AI 호출 P95 응답 시간 기존 + 30% 이내 (현재 ~3-5s → 목표 < 6.5s) | Grafana / 로그 타이머 |
| Cost | Gemini 입력 토큰 평균 1.5-2x 증가 허용, 출력 토큰은 현행의 2-3x 예상 — 월간 API 비용 증가율 < 150% | Gemini usage log |
| Accessibility | 섹션 구분·접이식 `aria-expanded`, 키보드 탐색 가능 | 수동 키보드 검증 + axe |
| Security | 신규 필드도 LegalGuardFilter 적용 + CI forbidden-terms 2차 패스 | 기존 가드 체인 재사용 |
| Backward compat | 기존 FE(`rationale`/`risks`) 일시 혼용 허용 — AiSignalResponse 필드 옵셔널 | 응답 JSON 검증 |
| i18n | 신규 필드 모두 Korean only (system prompt rule 1 준수) | 프롬프트 검증 + 응답 샘플링 |

---

## 4. Success Criteria

### 4.1 Definition of Done

- [ ] 신규 프롬프트 필드 4종 모두 응답 실데이터에 안정적으로 채워짐 (AAPL/TSLA/NVDA 3종목 수동 검증 각 3회 이상)
- [ ] FE 섹션 렌더 — 접이식 상호작용, 키보드 포커스, 다크모드 OK
- [ ] 뉴스 freshness 배지 (FRESH/RECENT/STALE) FE 노출
- [ ] BE 단위 + 통합 테스트 그린 (프롬프트 파싱 / 응답 매핑 / DB 저장)
- [ ] LegalGuardFilter + CI forbidden-terms 2차 패스 통과
- [ ] main PR squash merge
- [ ] gap-detector Match Rate ≥ 90%

### 4.2 Quality Criteria

- [ ] ESLint / tsc / forbidden-terms / BE check 모두 그린
- [ ] Gemini 응답 실패 시 기존 fallback (NEUTRAL) 경로 유지
- [ ] 신규 필드 null / 빈 배열 시 FE 섹션 숨김 (graceful degrade)
- [ ] 프롬프트 토큰 사용량 스냅샷 문서화 (before/after)

---

## 5. Risks and Mitigation

| Risk | Impact | Likelihood | Mitigation |
|------|:------:|:----------:|------------|
| 프롬프트 확장으로 Gemini 응답 JSON 파싱 실패율 증가 | High | Medium | 스키마 예시 다수 삽입 + LLM 응답 파서에 "필드 누락 허용, 타입 오류만 에러" 완화. `@JsonInclude(NON_NULL)` 활용 |
| 토큰 증가로 응답 지연 악화 | Medium | Medium | 출력 JSON 을 평탄화 + 불필요 설명 제한(문장 길이 제약). 필요 시 Gemini streaming 검토는 별도 이슈 |
| 초보자 해설이 투자 자문으로 해석될 수 있는 문구 유발 | High | Medium | forbidden-terms CI + LegalGuardFilter 재사용. Design 에서 "설명형 서술만 허용" 예제 가이드 삽입 |
| DB 스키마 확장 migration 충돌 | Medium | Low | JSONB 단일 컬럼으로 시작 — 이후 컬럼 분리는 별 feature 로 연기 가능 |
| 뉴스 5건 확대 시 Finnhub rate limit 부담 | Low | Low | NewsService 의 24h translated 캐시 재사용. DEFAULT_LIMIT 조정만 |
| FE 접이식 구조가 모바일에서 복잡도 증가 | Medium | Medium | 모바일 기본 펼침 전략을 Design 에서 확정 (핵심 신호 + 해설 + 뉴스만 기본) |
| 레거시 `rationale` / `risks` 와 신규 필드 중복으로 혼란 | Medium | Medium | Design §전환 전략 섹션에서 "deprecate 일정" 명시 — 본 feature 는 공존, 후속 feature 에서 rationale 제거 |

---

## 6. Architecture Considerations

### 6.1 Project Level Selection

| Level | Characteristics | Recommended For | Selected |
|-------|-----------------|-----------------|:--------:|
| Starter | Simple structure | Static sites | ☐ |
| **Dynamic** | Feature-based modules, BaaS integration | Web apps with backend | ✅ |
| Enterprise | Strict layer separation, DI, microservices | High-traffic systems | ☐ |

프로젝트 전역 Level 을 따릅니다 (bkit.config.json).

### 6.2 Key Architectural Decisions

| Decision | Options | Selected | Rationale |
|----------|---------|----------|-----------|
| 프롬프트 저장 위치 | classpath txt / DB / remote config | classpath txt (기존) | phase2.2 외부화 관례 유지, 버전관리 GitOps |
| 응답 스키마 확장 방식 | 필드 추가 / 버전 API 분기 | 필드 추가 (옵셔널) | 소비자 단일(FE) + 과도기 공존 전략 간단 |
| DB 저장 구조 | JSONB 단일 / 컬럼 분리 | JSONB 단일 (1차) | 스키마 flux 단계라 flex 우선, 조회 쿼리 요구 없음 |
| 뉴스 freshness 위치 | ContextAssembler 에서 산출 / FE 계산 | ContextAssembler (BE) | 응답 단일 소스. AI도 freshness 를 판단에 쓰도록 |
| FE 섹션 UI | 접이식 `<details>` / 커스텀 토글 | 커스텀 토글 (Tailwind 기반) | 접근성 속성 직접 제어, 다크모드 일관 |
| 용어 tooltip | 기존 `IndicatorTooltips` 재사용 / 신규 | 기존 재사용 | 중복 회피, 단일 소스 |

### 6.3 Clean Architecture Approach

```
Dynamic Level — 기능 추가만 (신규 레이어 없음)

BE:
  apps/api/src/main/java/com/aistockadvisor/ai/
    ├── domain/AiSignalResponse.java         # 확장
    ├── service/ContextAssembler.java        # 뉴스 freshness 확장
    ├── service/PromptBuilder.java           # system prompt 외부파일
    └── infra/AiSignalAuditEntity.java       # JSONB 컬럼 추가
  apps/api/src/main/resources/
    ├── prompts/ai-signal.system.txt         # 스키마 확장
    └── db/migration/V16__ai_audit_extension.sql

FE:
  apps/web/src/features/stock-detail/ai-signal/
    ├── ai-signal-panel.tsx                  # 섹션 리팩터
    ├── components/
    │    ├── beginner-explanation.tsx        # 신규
    │    ├── indicator-interpretation.tsx    # 신규
    │    ├── news-impact.tsx                 # 신규
    │    └── what-to-watch.tsx               # 신규
    └── types/ai-signal.ts                    # 확장
```

---

## 7. Convention Prerequisites

### 7.1 Existing Project Conventions

- [x] `CLAUDE.md` 섹션 존재 — FE kebab-case / BE PascalCase / 환경변수 prefix 정책
- [x] `.eslintrc` / `tsconfig` / Gradle check 기존 설정 재사용
- [x] forbidden-terms 4-level guard (Phase 2 RAG pipeline design §7) 재사용

### 7.2 Conventions to Define/Verify

| Category | Current State | To Define | Priority |
|----------|---------------|-----------|:--------:|
| Naming | 존재 | 신규 FE 컴포넌트는 kebab-case 파일 + PascalCase export | High |
| Folder structure | 존재 | `features/stock-detail/ai-signal/components/` 하위 구성 | High |
| Error handling | 존재 | AiSignal 파싱 실패 시 기존 NEUTRAL fallback 유지 | Medium |

### 7.3 Environment Variables Needed

신규 없음 — 기존 `GEMINI_API_KEY` 재사용.

### 7.4 Pipeline Integration

- 9-phase Development Pipeline 상 Phase 3 (기능 개발) 범위. 별도 스키마/컨벤션 문서 추가 불필요.

---

## 8. Next Steps

1. [ ] `/pdca design ai-analysis-deepening` — 스키마 / 시퀀스 / 섹션 UI 확정
2. [ ] Gemini API 로 확장 스키마 dry-run 샘플 응답 3-5회 확보 (design 부록)
3. [ ] DB V16 migration 작성
4. [ ] FE 섹션 컴포넌트 4개 + 기존 panel 리팩터
5. [ ] `/pdca analyze` → merge → archive

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 0.1 | 2026-04-21 | Initial draft | wonseok-han |
