# ai-signal-ux Planning Document

> **Summary**: AI 시그널 패널의 라벨·용어 명확화 + 차트 tf 분리 + 단기/장기 관점 동시 제공 (Gemini 1회 호출)
>
> **Project**: 지금이니?! (Nowini)
> **Version**: v0.1.0-beta
> **Author**: wonseok-han
> **Date**: 2026-04-24
> **Status**: Draft

---

## Executive Summary

| Perspective | Content |
|-------------|---------|
| **Problem** | 시그널 라벨("강한 긍정/부정")이 모호하고, "신뢰도"가 주가 확률로 오인되며, 금지 용어 노출 존재. 차트 tf 변경 시 동일 데이터로 Gemini 최대 6회 중복 호출. 단일 관점만 제공되어 투자 시야가 제한적 |
| **Solution** | 라벨·용어 명확화 + 차트 tf와 AI 시그널 디커플링 + Gemini 1회 호출로 단기(기술 지표)·장기(펀더멘탈) 두 관점 동시 출력. 소형주는 graceful degradation |
| **Function/UX Effect** | 종목 진입 시 단기/장기 방향 신호를 한눈에 파악. 차트 탭 전환과 무관하게 AI 분석 유지. 금지 용어 0건 달성 |
| **Core Value** | 초보 투자자의 오해 방지 + Gemini 토큰 효율화 + 법적 컴플라이언스 강화 + 분석 깊이 향상 |

---

## 1. Overview

### 1.1 Purpose

AI 시그널 패널을 3가지 축으로 개선:
1. **UX 명확화**: 라벨·용어·해석 가이드로 오해 방지 + 법적 컴플라이언스
2. **tf 분리**: 차트 타임프레임과 AI 시그널 독립 → 중복 Gemini 호출 제거
3. **듀얼 관점**: 단기(기술 지표 중심) + 장기(펀더멘탈 중심) 동시 제공

### 1.2 Background

**UX 문제:**
- "강한 긍정/부정" → 감성 분석처럼 읽힘 (방향성 전달 실패)
- "신뢰도 75%" → "75% 확률로 오른다"로 오인 가능
- 미리보기에 "매수/매도" 금지 용어 노출 (07-legal-compliance.md §7.2 위반)

**구조 문제:**
- 차트 탭(1D~5Y) 전환 시 AI 시그널도 재조회 → 동일 데이터로 캐시 키만 다른 Gemini 호출 중복
- ContextAssembler에 tf 전달하지만 실제 데이터 수집에 영향 없음 (Quote, Indicator, News 모두 동일)
- 단일 관점만 제공 → 투자 시야 제한

**기회:**
- 애널리스트 컨센서스/EPS 실적 데이터가 BE에 이미 존재 (PR #32) but ContextAssembler에 미연동
- Gemini 1회 호출로 두 관점 출력 가능 (공통 컨텍스트 중복 방지)

### 1.3 Related Documents

- 법적 준수: `docs/planning/07-legal-compliance.md`
- AI 전략: `docs/planning/05-ai-strategy.md`
- 애널리스트 컨센서스: PR #32 (`feat/analyst-estimates`)
- AI 분석 고도화: `docs/archive/2026-04/ai-analysis-deepening/`

---

## 2. Scope

### 2.1 In Scope

**A. UX 라벨/용어 (FE)**
- [ ] 시그널 라벨 → 방향성 용어 (강한 긍정→강한 상승 신호)
- [ ] "신뢰도" → "분석 확신도" + (?) 툴팁
- [ ] 미리보기 "매수/매도" 금지 용어 제거
- [ ] 시그널 해석 가이드 접이식 섹션 추가
- [ ] 정합도 배지 → 시그널 히어로 바로 아래로 이동

**B. 차트 tf ↔ AI 시그널 분리 (FE + BE)**
- [ ] FE: `stock-detail-view`에서 AI 시그널에 tf 전달 제거
- [ ] BE: `AiSignalController`에서 tf 파라미터 제거/무시
- [ ] 캐시 키 단순화: `ai:AAPL:1D:v2` → `ai:AAPL:v3`

**C. 단기/장기 듀얼 관점 (BE + FE)**
- [ ] ContextAssembler: 애널리스트 목표가 + EPS 실적 + 52주 고저 컨텍스트 추가
- [ ] 프롬프트 v3: 단기/장기 두 관점 동시 출력 스키마
- [ ] AiSignal 도메인: `short_term` + `long_term` 구조
- [ ] FE: 두 관점 동시 렌더링

### 2.2 Out of Scope

- 캔들 배치 안정화 (Yahoo throttle) — 별도 기능
- 캔들 FE 슬라이싱 최적화 (5Y 한 번 받기) — 별도 기능
- 시그널 색상 체계 변경 — 사용자 추후 결정
- 모바일 앱 대응

---

## 3. Requirements

### 3.1 Functional Requirements

| ID | Requirement | Priority | Status |
|----|-------------|----------|--------|
| FR-01 | 5단계 시그널 라벨을 방향성 용어로 변경 | High | Pending |
| FR-02 | "신뢰도" → "분석 확신도" + 툴팁 | High | Pending |
| FR-03 | 미리보기 금지 용어 제거 | High | Pending |
| FR-04 | 시그널 해석 가이드 접이식 섹션 | Medium | Pending |
| FR-05 | 정합도 배지 위치 상단 이동 | Medium | Pending |
| FR-06 | 차트 tf와 AI 시그널 디커플링 | High | Pending |
| FR-07 | Gemini 1회 호출 → 단기/장기 두 관점 동시 출력 | High | Pending |
| FR-08 | ContextAssembler에 애널리스트+EPS 데이터 추가 | High | Pending |
| FR-09 | 소형주 graceful degradation (애널리스트 데이터 없을 시 기술적 지표로 대체) | High | Pending |
| FR-10 | FE 두 관점 동시 렌더링 | High | Pending |

### 3.2 Non-Functional Requirements

| Category | Criteria | Measurement Method |
|----------|----------|-------------------|
| 법적 준수 | UI 금지 용어 0건 | CI forbidden-terms 워크플로우 |
| 토큰 효율 | 차트 탭 전환 시 Gemini 추가 호출 0건 | 로그 확인 |
| 접근성 | 툴팁·배지 키보드/스크린리더 접근 | 수동 검증 |
| 성능 | Gemini 응답 시간 기존 대비 +30% 이내 | 로그 latency |

---

## 4. Detailed Design Decisions

### 4.1 시그널 라벨 매핑

| 기존 | 변경 |
|---|---|
| 강한 긍정 (STRONG_BUY) | **강한 상승 신호** |
| 긍정 (BUY) | **상승 신호** |
| 중립 (NEUTRAL) | **중립** |
| 부정 (SELL) | **하락 신호** |
| 강한 부정 (STRONG_SELL) | **강한 하락 신호** |

### 4.2 "신뢰도" → "분석 확신도"

- `분석 확신도 75%` + `(?)` 아이콘
- 호버: "AI가 자체 분석에 대해 느끼는 확신 수준입니다. 주가 예측 확률이 아닙니다."

### 4.3 Gemini 1회 호출 → 듀얼 관점 응답 스키마 (v3)

```json
{
  "short_term": {
    "signal": "BUY",
    "confidence": 0.72,
    "rationale": ["..."],
    "risks": ["..."],
    "summary_ko": "...",
    "beginner_explanation": "...",
    "indicator_interpretation": [...],
    "news_impact": [...],
    "what_to_watch": ["..."]
  },
  "long_term": {
    "signal": "NEUTRAL",
    "confidence": 0.55,
    "rationale": ["..."],
    "risks": ["..."],
    "summary_ko": "...",
    "beginner_explanation": "...",
    "indicator_interpretation": [...],
    "news_impact": [...],
    "what_to_watch": ["..."]
  }
}
```

### 4.4 단기 vs 장기 컨텍스트 가중치

| 데이터 | 단기 (1~2주) | 장기 (6개월~1년) |
|---|---|---|
| RSI, MACD, 볼린저, MA5/20 | 핵심 | 참고 |
| MA60, 장기 추세 | 참고 | 핵심 |
| 최근 뉴스 (FRESH/RECENT) | 핵심 | 가중치 낮음 |
| 애널리스트 목표가/컨센서스 | 참고 | 핵심 |
| EPS 실적 (beat/miss) | 참고 | 핵심 |
| 52주 고저 대비 현재 위치 | 참고 | 핵심 |
| P/E, 시총, 섹터 | 참고 | 핵심 |

### 4.5 소형주 Graceful Degradation

애널리스트 커버리지 없는 종목 처리:
- ContextAssembler: `analyst_estimates` → null (기존 null-safe 패턴)
- 프롬프트 지시: "analyst_estimates가 null이면 기술적 장기 추세 + 기업 기본 정보로 분석. '애널리스트 커버리지가 없어 기술적/기본적 지표 위주로 분석했습니다' 명시"
- FE: 장기 관점 자체는 항상 표시 (데이터 범위 내에서 의미 있는 분석 제공)

---

## 5. Success Criteria

### 5.1 Definition of Done

- [ ] 시그널 라벨 방향성 용어 + "분석 확신도" 리네이밍
- [ ] 미리보기 금지 용어 0건
- [ ] 차트 탭 전환 시 AI 시그널 재조회 없음
- [ ] 단기/장기 두 관점 동시 표시
- [ ] 소형주에서 장기 관점 graceful degradation 동작
- [ ] `make check` 통과

### 5.2 Quality Criteria

- [ ] CI forbidden-terms 워크플로우 통과
- [ ] 다크/라이트/브랜드 3테마 정상 렌더링
- [ ] 키보드/스크린리더 접근성 유지
- [ ] Gemini 응답 latency 허용 범위 이내

---

## 6. Risks and Mitigation

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| Gemini 응답 길이 증가로 latency 상승 | Medium | Medium | 기존 ~10초 대비 +30% 이내 목표, 초과 시 관전 포인트 생략 |
| v3 스키마로 Gemini 파싱 실패율 증가 | High | Low | ResponseValidator 검증 강화 + fallback neutral 유지 |
| 소형주 장기 관점 품질 저하 | Low | Medium | 명시적 고지 + 가용 데이터 범위 내 분석 |
| 기존 캐시(v2) 무효화 | Low | Low | 키 버전 변경(v3)으로 자동 분리 |

---

## 7. Architecture Considerations

### 7.1 변경 범위

**BE 변경:**

| 파일 | 변경 |
|---|---|
| `ContextAssembler` | 애널리스트 목표가 + EPS 실적 + 52주 고저 컨텍스트 추가 |
| `ai-signal.system.txt` | v3 듀얼 관점 스키마 + 단기/장기 분석 지시 |
| `AiSignal.java` | `shortTerm` + `longTerm` 구조로 변경 |
| `ResponseValidator` | v3 스키마 검증 |
| `AiSignalService` | tf 파라미터 제거, 캐시 키 `ai:{ticker}:v3` |
| `AiSignalController` | `?tf=` 쿼리 파라미터 제거 |

**FE 변경:**

| 파일 | 변경 |
|---|---|
| `ai-signal-panel.tsx` | 라벨/용어 + 듀얼 관점 렌더링 + 해석 가이드 + 정합도 배지 이동 |
| `stock-detail-view.tsx` | AI 시그널에 tf 전달 제거 |
| `use-ai-signal.ts` | queryKey에서 tf 제거 |
| `ai-signal.ts` (타입) | v3 응답 타입 |
| `ai-accuracy-badge.tsx` | aria-label 용어 일치 |
| 신규: `signal-guide.tsx` | 시그널 해석 가이드 컴포넌트 |

---

## 8. Next Steps

1. [ ] Write design document (`ai-signal-ux.design.md`)
2. [ ] Start implementation

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 0.1 | 2026-04-24 | Initial draft (UX 라벨/용어만) | wonseok-han |
| 0.2 | 2026-04-24 | tf 분리 + 듀얼 관점 + graceful degradation 추가 | wonseok-han |
