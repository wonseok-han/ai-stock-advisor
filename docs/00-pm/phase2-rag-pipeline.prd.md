# phase2-rag-pipeline - Product Requirements Document

> **Date**: 2026-04-14
> **Author**: wonseok-han
> **Method**: bkit PM Agent Team (pm-lead single-session synthesis)
> **Status**: Draft
> **Based on**: [pm-skills](https://github.com/phuryn/pm-skills) by Pawel Huryn (MIT)
> **Prev PRD**: `docs/archive/2026-04/mvp/mvp.prd.md` (Phase 1 완료, Match Rate 94%)

---

## Executive Summary

| Perspective | Content |
|-------------|---------|
| **Problem** | Phase 1 이 차트·지표를 한국어로 "보여주기"까지는 성공했지만, 초보 투자자는 여전히 **"영어 뉴스를 읽어야 하고"**, **"지표가 많아진 만큼 종합 판단이 더 어려워졌다"**. 분석은 여전히 사용자 머릿속에서 조립된다. |
| **Solution** | **RAG 기반 한국어 뉴스 요약 (F5)** + **AI 종합 시그널 (F4)** 을 Phase 1 종목 상세 페이지에 부착. Spring Boot 가 지표·뉴스·시장맥락을 서버 측에서 조립(RAG) → Gemini Flash 가 JSON(signal/confidence/rationale/risks) 으로 판단. 4-level 금지 용어 가드 + 프롬프트 제약으로 **"투자 자문 아님"** 을 코드/프롬프트/응답/CI 모든 레이어에서 강제. |
| **Target User** | Phase 1 과 동일한 한국어 라이트 투자자 (Primary: IT 25~35세 개발자 신규 해외주식 투자자). **Phase 2 로 세그먼트 확장 없음** — Phase 1 에서 진입한 사용자의 "머릿속 조립" 부담을 제거하는 데 집중. |
| **Core Value** | **"보여주기"→"이해시키기" 전환**. 3분 안에 한국어로 이 종목의 분위기와 그 근거·리스크를 설명받음. 할루시네이션·프롬프트 인젝션·자본시장법 리스크를 RAG + 금지용어 가드 + 캐시로 동시에 관리하며 월 LLM 비용 $5~10 유지. |

---

## 1. Opportunity Discovery

### 1.1 Desired Outcome

> **"Phase 1 사용자가 종목 상세 페이지에서 단순히 '차트를 보러' 오는 것이 아니라, '오늘 이 종목의 분위기가 어떤지 한국어로 이해하러' 오게 된다."**

측정 지표 (Activation → Engagement):

- **종목 상세 체류 시간**: Phase 1 대비 **+50%** (60s → 90s+ 목표)
- **뉴스 카드 클릭/확장률**: 상세 페이지 방문의 **≥ 45%**
- **AI 시그널 섹션 스크롤 도달률**: 상세 페이지 방문의 **≥ 60%**
- **AI 시그널 "rationale 전체 펼치기" 클릭률**: 도달자의 **≥ 35%** (단순 배지 스캔이 아닌 "근거까지 읽는" 사용자 비율)
- **LLM 캐시 hit ratio**: ≥ **70%** (MVP 설계 목표 유지)

### 1.2 Opportunity Solution Tree

```
Outcome: 종목 상세에서 "한국어로 오늘 분위기 이해"를 완결한다
│
├── Opportunity A: 영어 뉴스가 Phase 1 UI 에 원문 그대로 남아 있어 소비 단절
│   ├── Solution A1: Finnhub /company-news 수집 + Gemini 한국어 번역·3줄 요약
│   ├── Solution A2: 24h 기사 단위 Postgres 캐시 (동일 기사 재번역 $0)
│   └── Solution A3: "원문 보기" 토글 유지 (저작권/신뢰도 확보)
│
├── Opportunity B: Phase 1 지표·뉴스가 병렬 존재하지만 "종합 판단"은 사용자 몫
│   ├── Solution B1: 서버 측 ContextAssembler (지표 + 뉴스 + 시장맥락 JSON)
│   ├── Solution B2: Gemini Flash → 구조화 JSON (signal/confidence/rationale/risks)
│   ├── Solution B3: AI 시그널 카드 UX (배지 + 게이지 + 근거/리스크 리스트)
│   └── Solution B4: signal 값 변화 시 재방문 트리거 (Phase 4 알림 연계 준비)
│
├── Opportunity C: LLM 의 "확정적 어조" 가 초보에게 "추천"으로 오독될 위험
│   ├── Solution C1: 프롬프트에 "확정 금지, 가능성·경향 어조" 규칙 강제
│   ├── Solution C2: ResponseValidator (금지 용어 검출 시 재시도 1회, 실패 시 중립)
│   ├── Solution C3: AI 카드 내부 고정 면책 (`components/legal/disclaimer-banner` 재사용)
│   └── Solution C4: 4-level 금지 용어 가드 (상수 + 프롬프트 + validator + CI grep)
│
├── Opportunity D: 비용·레이턴시가 1인 개발 운영을 위협할 위험
│   ├── Solution D1: 종목 단위 Redis 1h 캐시 (signal) + Postgres 24h 캐시 (뉴스 번역)
│   ├── Solution D2: partial response (뉴스만 성공해도 UI 일부 hydrate)
│   ├── Solution D3: 비용 모니터링 대시보드 지표 (Phase 2 report 기준)
│   └── Solution D4: Gemini rate limit (분 15 RPM) 대비 Bucket4j 역-레이트리미터
│
└── Opportunity E: 뉴스 LLM 요약의 "원문 왜곡" 리스크 (사실과 다른 요약)
    ├── Solution E1: 요약 프롬프트에 "헤드라인 의미 유지, 감성·주체 보존" 규칙
    ├── Solution E2: 요약 출력에 sentiment(positive/neutral/negative) 구조화
    └── Solution E3: 파일럿 50건 수동 스팟체크 → 왜곡 ≤ 5%
```

### 1.3 Prioritized Opportunities

Importance × (1 - Satisfaction) (Ulwick Opportunity Score):

| # | Opportunity | Importance | Satisfaction | Score |
|---|-------------|:----------:|:------------:|:-----:|
| 1 | **B: 종합 판단을 AI 가 대신해 준다** (핵심 킬러) | 0.95 | 0.10 | **0.86** |
| 2 | **A: 영어 뉴스 한국어 번역·요약** | 0.85 | 0.20 | **0.68** |
| 3 | **C: "추천"으로 오독 방지 (법적/UX)** | 0.90 | 0.30 | **0.63** |
| 4 | **D: 비용·가용성 유지** | 0.75 | 0.40 | **0.45** |
| 5 | **E: 요약 왜곡 방지** | 0.70 | 0.45 | **0.39** |

→ **B (AI 시그널) 이 Phase 2 단일 킬러**. A (뉴스 번역) 가 "왜 그 판단인지" 설명하는 **지지 피처**. C 가 **안전판**. D/E 는 **운영/품질 전제**.

### 1.4 Recommended Experiments

| # | Tests Assumption | Method | Success Criteria |
|---|-----------------|--------|------------------|
| 1 | AI rationale/risks 가 초보에게 "납득된다" | 10명 파일럿 세션 녹화 + 5점 만족도 | 평균 ≥ 4.0 / 5.0 |
| 2 | Gemini Flash 한국어 품질이 GPT-4o-mini 대비 "쓸 만함" | 동일 입력 30종목 blind 비교 | 품질 차이 ≤ 0.5점 |
| 3 | LLM prompt injection 방어 (티커 입력에 악의적 지시) | 레드팀 20 케이스 | 모두 무력화 |
| 4 | "확정적 어조" 제거 프롬프트가 실제 출력을 제어함 | 100회 호출 샘플 금지용어 grep | 검출 0건 |
| 5 | 뉴스 요약이 원문을 왜곡하지 않는다 | 수동 스팟체크 50건 | 왜곡 ≤ 5% |
| 6 | 캐시 hit ratio 70% 목표가 실측 가능 | 1주 운영 로그 | hit ratio ≥ 70% |

---

## 2. Value Proposition & Strategy

### 2.1 JTBD Value Proposition (6-Part)

| Part | Content |
|------|---------|
| **Who** | Phase 1 을 써본 한국어 라이트 투자자. 차트·지표는 봤지만 "그래서 지금 이 종목 분위기가 어떻다는 거야?" 에 여전히 답을 못 찾는 사람 |
| **Why** | 데이터가 한 화면에 모인 것만으로는 **"의미 해석"** 이 되지 않음. 영어 뉴스는 여전히 읽기 부담, 지표는 여러 개라 종합 판단이 머리속 작업으로 남음 |
| **What Before** | Phase 1 상세 페이지 → 차트/지표 확인 → **새 탭에서 Yahoo/인베스팅 뉴스 검색** → 유튜브로 이동 → 대충 감으로 판단 |
| **How** | 같은 종목 상세 페이지 하단에 **(1) 한국어 뉴스 요약 카드** + **(2) AI 종합 시그널 카드** 추가. 뉴스는 3줄 한국어 요약 + 감성 태그, AI 시그널은 signal 배지 + 신뢰도 게이지 + rationale/risks 리스트 |
| **What After** | 한 페이지 안에서 "오늘 AAPL 분위기: 약한 매수 시그널, 근거는 실적·MACD 양전환, 리스크는 FOMC" 까지 **한국어로** 읽고 증권앱으로 바로 이동 |
| **Alternatives** | (a) Yahoo Finance + 구글 번역 (b) 유튜브 종목 분석 (c) Seeking Alpha ($19/월) (d) 카톡 투자방 — 우리는 **한국어 × 실시간 × 객관 × 무료 × 면책** 조합 |

**Value Proposition Statement**:
> "Phase 1 이 미국 주식 데이터를 한국어로 **보여줬다면**, Phase 2 는 **이해시킨다**. 서버가 조립한 RAG 컨텍스트 위에서 AI 가 '분위기 + 근거 + 리스크' 를 한국어로 정리한다 — 매수/매도 권유 없이, 참고용으로."

### 2.2 Lean Canvas

| Section | Content |
|---------|---------|
| **Problem** | 1) 영어 뉴스 소비 단절 (Phase 1 미해결) 2) 지표 많아져 종합 판단 부담 증가 3) "지금 분위기" 를 한국어로 한 줄 요약해 주는 도구 없음 |
| **Solution** | 1) Gemini Flash 뉴스 한국어 요약(3줄) + 감성 태그 2) RAG 기반 AI 시그널 JSON (signal/confidence/rationale/risks) 3) 4-level 금지용어 가드 + 프롬프트 제약 |
| **UVP** | "미국 주식의 오늘 분위기, AI 가 한국어로 3분 안에 정리해 줍니다 — 참고용, 무료" |
| **Unfair Advantage** | (a) Phase 1 에서 검증된 "법적 분석 도구" 포지셔닝 (b) 서버 측 RAG 로 할루시네이션·인젝션 최소화 (c) 4-level 금지용어 가드 (코드/프롬프트/응답/CI) 는 카피하기 어려운 운영 노하우 (d) 무료 유지 → 자본시장법 "대가" 요건 회피 |
| **Customer Segments** | Primary: IT 25~35세 개발자 신규 해외주식 투자자 (Phase 1 에서 확장 안 함). Secondary: 포트폴리오 2년차 자기주도 투자자. Tertiary: SEO 유입 일회성 탐색자 |
| **Channels** | Phase 1 과 동일 + **"AI 시그널 공개" 를 Phase 2 Launch 포스트 단일 hook 으로** (긱뉴스/HN KR/디스콰이엇 재포스트, X/Twitter 개발자 타임라인) |
| **Revenue Streams** | **없음 (무료 유지)** — 유료화 시 자본시장법 "대가" 요건 충족 → 유사투자자문업 신고 필요 (Phase 5+ 재검토) |
| **Cost Structure** | Phase 1 기반 (Vercel/Fly Free $0) + **LLM $5~10/월** (뉴스 $2 + AI signal $3 + 여유분 $5) + 도메인 $1/월 ≈ **$6~11/월** 유지 |
| **Key Metrics** | 종목 상세 체류시간, AI rationale 펼치기 클릭률, 캐시 hit ratio (≥70%), 월 LLM 비용 (≤$15), 금지용어 검출 (=0), signal JSON 파싱 실패율 (<1%) |

---

## 3. Market Research

### 3.1 User Personas (Phase 1 계승, Phase 2 접점 재정의)

#### Persona 1: "초보 지후" (Beachhead — Phase 1/2 동일)

| Attribute | Details |
|-----------|---------|
| **Demographics** | 29세, IT 스타트업 주니어 엔지니어, 서울, 해외주식 6개월차 |
| **Primary JTBD (Phase 2)** | 뉴스에서 AAPL 언급 보고 검색 → 차트·지표 확인 후에도 **"그래서 살까 말까?"** 판단 근거가 부족. AI 가 "이럴 땐 이런 점을 주목하세요" 를 한국어로 짚어주기를 원함 |
| **Phase 2 New Pain** | (Phase 1 해결) 차트·지표 한국어 표시됨 → (Phase 2 남은 pain) 영어 뉴스 읽기 싫음 + 지표 여러 개 종합하기 어려움 |
| **Phase 2 Desired Gain** | 뉴스는 3줄 한국어 요약으로, AI 는 "긍정/부정 근거" 를 짧게 정리. **자기가 지표를 해석할 필요 없이** 결론 + 근거만 읽고 싶음 |
| **Unexpected Insight (from MVP)** | 기술지표보다 **뉴스 요약**을 훨씬 자주 봄 (MVP 리포트 §6.1). Phase 2 는 이 insight 를 정면으로 활용 — **F5 뉴스 + F4 AI 가 진짜 핵심**, F2/F3 차트/지표는 "근거 디스플레이" 로 포지션 조정 |
| **Product Fit (Phase 2)** | F5 (뉴스 한국어 요약) 가 가장 먼저 닿는 접점. F4 (AI 시그널) 가 "종합 정리" 킬러 기능. |

#### Persona 2: "자기주도 주희" (세컨드 오피니언 수요자)

| Attribute | Details |
|-----------|---------|
| **Demographics** | 34세, 프리랜서 UX 디자이너, 해외주식 2년차, 5~10종목 포트폴리오 |
| **Primary JTBD (Phase 2)** | 보유 종목 일일 모니터링. 자기 판단은 이미 있고, **AI 의 판단과 "다른지/같은지"** 를 보며 맹점 보정 |
| **Phase 2 New Pain** | Phase 1 만으로는 자기가 이미 보던 데이터와 크게 다르지 않음. AI 의 판단이 있어야 "이 도구 고유 가치" 체감 |
| **Phase 2 Desired Gain** | AI rationale 중 **자기가 놓친 관점** (예: "VIX 14 로 시장 안정") 발견. AI 시그널이 자기 판단과 어긋날 때 재확인 트리거 |
| **Unexpected Insight** | **"AI 를 권위로 쓰지 않고 second opinion 으로 씀"** → signal 값 그 자체보다 rationale/risks 의 "관점 다양성" 이 가치. AI rationale 개수 2~5개 강제는 이 페르소나에게 특히 유효 |
| **Product Fit (Phase 2)** | AI 시그널 rationale/risks 가 핵심. Phase 4 (북마크/알림) 와 연계해 "signal 변화 시 알림" 가치 ↑ |

#### Persona 3: "호기심 현우" (일회성 탐색 — 가장 오독 위험 큰 세그먼트)

| Attribute | Details |
|-----------|---------|
| **Demographics** | 41세, 중소기업 팀장, 유튜브 보고 소액 매매, 검색 유입 중심 |
| **Primary JTBD (Phase 2)** | 뉴스에서 본 종목(NVDA 등) 검색 → 1~2분 안에 "분위기만" 확인 |
| **Phase 2 New Pain** | AI 시그널의 **숫자(신뢰도 0.68) 를 "확정"으로 오독할 위험** — 가장 큰 세그먼트 |
| **Phase 2 Desired Gain** | 배지(약한 매수) + 짧은 요약 1~2줄 읽고 떠나고 싶음. 자세한 rationale 까지는 안 읽음 |
| **Unexpected Insight** | Phase 1 회고에서 "지표 툴팁 한국어 해설" 이 유효했음 (report §6.1). **Phase 2 는 AI 카드에도 "해설/면책 UX" 가 제1 우선**. "참고용" 각인이 안 되면 법적 리스크 직결 |
| **Product Fit (Phase 2)** | F4 AI 카드 상단에 고정 면책 + "신뢰도는 확률이며 예측이 아님" 인라인 툴팁. summary_ko 마지막에 면책 문장 강제 |

### 3.2 Competitive Landscape (Phase 2 문맥에서 재평가)

| Competitor | Strengths | Weaknesses vs Phase 2 | Our Opportunity (Phase 2) |
|-----------|-----------|----------------------|---------------------------|
| **Seeking Alpha** | 전문가 레포트 심층, 커뮤니티, 강세/약세 심리 측정 | 영어, $19~/월 유료 공격적, 초보엔 깊이 과도, AI 시그널은 pro 티어 | **한국어 × 무료 × AI 요약** 조합 — 초보용 "Seeking Alpha lite" 포지션 |
| **Simply Wall St** | 시각화(스노우플레이크) + 재무 분석 AI, 초보 친화 | 영어, 재무 중심(뉴스/단기 시그널 약함), 부분 유료 | **뉴스 + 단기 시그널 + 한국어** 로 차별화. 재무 심층은 우리 범위 아님 (Phase 5+) |
| **Webull (AI Stock Ratings)** | 증권사 + AI 레이팅, 체결 통합, US 대형 | 한국어 빈약, 국내 계좌 연동 없음, 리테일 직구 불편 | **독립 분석 도구** 로 한국 증권사(토스/키움)와 상호보완 (우리에서 이해 → 증권사에서 매매) |
| **Composer.trade** | AI 전략 빌더, no-code 알고 트레이딩, 백테스팅 | 알고 트레이딩 지향 (초보 진입장벽↑), 영어, 포지셔닝 다름 | 우리는 **"전략 빌더 아닌 분석 도구"** — 타겟 자체가 다름 |
| **Zacks** | Quant 레이팅(Zacks Rank), 애널리스트 컨센서스 집계 | 영어, UI 노후, AI 아닌 규칙 기반, 초보엔 과도 | **AI × 한국어 × 실시간 뉴스 반영** 으로 차별. Zacks Rank 류의 "참고 점수" 문화를 한국어로 현지화 |

**Differentiation (Phase 2 업데이트)**:
> Phase 1 의 **"한국어 × 초보 친화 × 무료"** 에 Phase 2 는 **"AI 종합 시그널 + 뉴스 한국어 요약"** 을 추가. 해외 경쟁자는 영어이거나 유료이고, 국내 경쟁자(증권사 앱)는 AI 해석 레이어가 없다. Phase 2 의 **4-level 금지 용어 가드 + 서버 측 RAG** 는 카피하기 어려운 운영 노하우 — 자본시장법 리스크 관리가 곧 moat.

### 3.3 Market Sizing (Phase 1 기반, Phase 2 업데이트 없음)

Phase 2 는 **새 세그먼트 확장이 아닌 기존 세그먼트 심화**이므로 TAM/SAM/SOM 수치는 Phase 1 PRD 와 동일하게 유지한다:

| Metric | Current | 3-Year |
|--------|---------|--------|
| **TAM** | 국내 해외주식 투자자 × 무료 도구 수용 ≈ 420만 MAU 잠재 | 500만 MAU |
| **SAM** | 한국어 라이트 유저 × 분석 도구 의향 ≈ 100만 MAU 잠재 | 150만 MAU |
| **SOM (3년)** | MVP 1년차 월 1~3만 MAU | 10~15만 MAU |

**Phase 2 가 SOM 에 미치는 영향**:
- AI 시그널은 "이 도구 고유 가치" 각인 요인. 재방문율(Week-4) 목표 20% → 30% 상향 여지
- 체류시간 60s → 90s 상승 시 SEO 유입(Persona 3) 의 page-depth 증가 → long-tail SEO 효과
- **새 세그먼트 확장 없음** — Phase 3 (시장 대시보드) 이 "메인 페이지 유입" 확장의 레버

---

## 4. Go-To-Market

### 4.1 Beachhead Segment (Phase 1 계승 확정)

Phase 1 Beachhead "**IT 업계 25~35세 개발자 신규 해외주식 투자자**" 를 **그대로 계승**. Phase 2 는 새 beachhead 를 찾지 않고, 기존 beachhead 의 retention + referral 을 심화하는 단계.

4-criteria 재평가 (Phase 2 문맥):

| Criteria | 해외주식 신규 (<1년) | **IT 25-35 개발자 (신규)** | 유튜브 종목 시청자 | 재테크 블로거 |
|----------|:----:|:----:|:----:|:----:|
| Burning Pain (Phase 2: 종합 판단 부담) | 5 | **5** | 4 | 3 |
| Willingness to Talk About AI 시그널 | 2 | **5** | 3 | 4 |
| Winnable Share (Phase 1 접점 있음) | 4 | **5** | 3 | 4 |
| Referral Potential (AI × 한국어 hook) | 3 | **5** | 4 | 5 |
| **Total** | 14 | **20** | 14 | 16 |

**Why IT 개발자 × Phase 2**:
1. Phase 1 Launch 에서 이미 유입된 beachhead — 재진입 유도만으로 활성화 가능
2. **AI 시그널 공개** 자체가 긱뉴스/HN KR 에서 "개발자 친화 topic" (LLM + RAG + 자본시장법 고려) → 커뮤니티 재진입 hook 강함
3. 오독 위험 최소 세그먼트 — Phase 2 가장 큰 리스크(신뢰도 숫자 오독)를 가장 늦게 겪음 → **초기 critical mass 에 최적**
4. Bug report 품질 높음 — 금지 용어 grep 누락, JSON 파싱 실패 같은 엣지 케이스 조기 발견

**90-Day Phase 2 Plan** (MVP 완료일 2026-04-14 기준):

| Day | Action |
|---|---|
| D0 ~ D14 | 뉴스 서비스 (F5) 구현 + 파일럿 50건 스팟체크 |
| D14 ~ D28 | AI 시그널 서비스 (F4) 구현 + 10종목 수동 QA + 프롬프트 튜닝 |
| D28 ~ D35 | 4-level 금지용어 가드 완성 + 레드팀 20 케이스 + CI grep 자동화 |
| D35 ~ D42 | 내부 파일럿 10명 (개발자 지인) — 만족도 ≥ 4.0 / 5.0 검증 |
| D42 | **v0.2 Launch** — 긱뉴스/HN KR 재포스트 ("Phase 1 공개 후 3주, AI 시그널 붙였습니다") |
| D42 ~ D60 | Retention Week-4 측정 + AI rationale 펼치기 클릭률 분석 |
| D60 ~ D90 | Phase 3 (시장 대시보드) 우선순위 결정 근거 수집 |

### 4.2 GTM Strategy

| Element | Details |
|---------|---------|
| **Channels** | 1) 긱뉴스/HN KR/디스콰이엇 재포스트 (Phase 1 공개 후 연속성) 2) X/Twitter 개발자 타임라인 3) 네이버 블로그 종목 SEO 페이지에 AI 시그널 embed 4) GitHub Public repo `CHANGELOG.md` 업데이트 |
| **Messaging (Primary)** | "Phase 1 은 미국 주식을 한국어로 **보여줬고**, Phase 2 는 **이해시킵니다**. AI 가 뉴스·지표·시장맥락을 종합해 분위기와 근거를 한국어로 정리 — 참고용, 무료." |
| **Messaging (Secondary — 개발자 hook)** | "Spring Boot + Gemini Flash RAG 파이프라인 공개. 4-level 금지 용어 가드로 자본시장법 대응 사례 공유." |
| **Success Metrics (90일)** | WAU 500 (Phase 1 + 150%), 종목 상세 체류 90s+, AI rationale 펼치기 35%+, 캐시 hit 70%+, LLM 월 비용 ≤$15, 금지용어 검출 0 |
| **Launch Gate** | (1) 10종목 수동 QA 통과 (2) 레드팀 20 케이스 전부 방어 (3) 내부 파일럿 10명 만족도 ≥ 4.0 (4) CI grep 통과 (5) 비용 모니터링 1주 운영 |
| **Anti-goals** | Phase 2 에서는 **시장 대시보드 (F6) 건드리지 않음** — Phase 3 범위. 북마크/알림도 Phase 4. 범위 scope creep 은 launch 지연의 가장 큰 리스크 (Phase 1 report §6.2 교훈: `/detail` Phase 조기 구현) |

---

## 5. Product Requirements (PRD)

### 5.1 Summary

Phase 2 는 Phase 1 에서 구축한 종목 상세 페이지 (`/stock/[ticker]`) 에 **(F5) 한국어 뉴스 요약 카드** 와 **(F4) AI 종합 시그널 카드** 를 부착한다. 서버(Spring Boot) 가 지표·뉴스·시장맥락을 조립(RAG)해 Gemini Flash 에 전달하고, 구조화 JSON 응답을 검증·캐시·렌더링한다. 4-level 금지 용어 가드(코드 상수 + 프롬프트 + response validator + CI grep)로 자본시장법 "유사투자자문업" 리스크를 코드 레벨에서 차단하며, 월 LLM 비용 $5~10 유지를 비용 가드레일로 삼는다.

Phase 2 완료 정의: **"AAPL / TSLA / NVDA 10종목에서 한국어 뉴스 3줄 요약과 AI 시그널이 정상 렌더링되고, 금지용어 검출 0건, 캐시 hit ≥70%, 월 LLM 비용 ≤$15 로 2주 연속 운영됨."**

### 5.2 Background & Context

**왜 지금 (Phase 1 완료 직후) Phase 2 인가?**

1. **Phase 1 Match Rate 94%** (2026-04-14) — 기술 지표·차트·검색 파이프라인이 안정. Phase 2 는 이 기반 위에 **"해석 레이어"** 를 얹는 단계
2. **Phase 1 설계에서 이미 예정됨** — `mvp.design.md` 의 `/news`, `/ai-signal` 엔드포인트와 RAG 컴포넌트(ContextAssembler / PromptBuilder / ResponseValidator) 가 Phase 2 scope 로 문서화됨 (MVP PRD §5.8, MVP report §2.4)
3. **Persona insight 업데이트** — Phase 1 pilot 관찰상 "기술지표보다 뉴스 요약을 더 자주 본다" (Persona 1) → **F5 가 F4 의 선행 피처**로 재정의
4. **법적 리스크는 Phase 2 에서 폭발적으로 증가** — AI 판단 출력이 들어가는 순간 "분석 도구 vs 유사투자자문업" 경계에 실질적으로 서게 됨. 4-level 가드를 Phase 2 내에 완성해야 v1.0 Public Launch 가능

**무엇이 가능해졌나?**
- Phase 1 에서 ContextAssembler 입력 재료(지표 + 시장 스냅샷 일부) 가 이미 준비됨 → Phase 2 는 뉴스 수집·번역·RAG 조립·LLM 호출·검증·캐시만 추가
- Gemini Flash 무료 티어(일 1M 토큰, 분 15 RPM) 및 JSON mode 가 안정적으로 제공됨
- Phase 1 의 Redis 캐시 인프라(Adapter + TTL 정책) 재활용 가능

**무엇이 여전히 제약인가?**
- Finnhub free `/company-news` 는 rate limit (60 req/min) 와 커버리지 제한 — 대형주는 OK, 중소형주는 공백. Phase 2 는 **대형주 우선** 으로 scope 축소
- Spring Boot 가상 스레드 기반이지만 LLM 호출 자체가 2~5s 소요 → **서버 내 parallel call 은 금지**, UX 는 **점진 hydrate (skeleton → news → signal)** 로 대응
- Gemini Flash 무료 티어 분당 15 RPM — 동시 트래픽 스파이크 방어 필요 (Bucket4j + Redis 큐)

### 5.3 Objectives & Key Results

| Objective | Key Result | Target (Phase 2 End) | Target (+90일) |
|-----------|-----------|:---:|:---:|
| **O1. 종목 상세에서 "한국어 이해 완결"** | 상세 페이지 평균 체류시간 | ≥ 75s (Phase 1 +25%) | ≥ 90s (+50%) |
| | AI 시그널 섹션 스크롤 도달률 | ≥ 55% | ≥ 60% |
| | AI rationale 펼치기 클릭률 | ≥ 30% | ≥ 35% |
| | 재방문율 (Week-4) | ≥ 25% | ≥ 30% |
| **O2. RAG 파이프라인 품질·안정성** | Gemini JSON 파싱 실패율 | < 2% | < 1% |
| | 금지용어 검출 (CI grep + response validator) | 0 | 0 |
| | 뉴스 요약 왜곡 (수동 스팟체크 50건) | ≤ 10% | ≤ 5% |
| **O3. 비용·가용성 가드레일** | 월 LLM 비용 | ≤ $10 | ≤ $15 (MAU 1K 기준) |
| | LLM 캐시 hit ratio | ≥ 65% | ≥ 70% |
| | AI 시그널 p95 응답 시간 (cache hit) | < 200ms | < 200ms |
| | AI 시그널 p95 응답 시간 (cache miss) | < 5s | < 5s |
| **O4. 법적 리스크 관리** | 4-level 금지용어 가드 구축 | 4/4 레이어 완료 | 유지 |
| | 면책 고지 카드 내부 노출 | 100% (AI 카드 + 뉴스 카드) | 100% |
| | 레드팀 prompt injection 방어율 (20 케이스) | 100% | 100% |

### 5.4 Market Segments

Phase 1 과 동일하며 Phase 2 문맥에서 접점만 업데이트:

- **Primary (Beachhead):** IT 25~35세 개발자 신규 해외주식 투자자 (지후) — **AI 시그널이 가장 잘 맞고 오독 위험 최소**
- **Secondary:** 자기주도 2년차 투자자 (주희) — **second opinion 수요, Phase 4 알림과 연계**
- **Tertiary:** 일회성 탐색자 (현우) — **가장 큰 오독 리스크, 면책 UX 최우선**

### 5.5 Value Propositions

- **Customer Jobs**: (a) 종목 검색 직후 분위기 한국어 이해 (b) 보유 종목 뉴스·AI 시그널 일일 스캔 (c) 세컨드 오피니언 확보
- **Gains Created**: 한국어 뉴스 3줄 요약 / AI 종합 시그널 / 근거·리스크 구조화 / 무료 / 참고용 안심감
- **Pains Relieved**: 영어 번역 수고 / 지표 여러 개 종합 부담 / 유튜브 편향·시의성 / 유료 도구 부담 / "추천" 으로 오독되는 불안
- **Reference**: Section 2.1 JTBD 6-Part

### 5.6 Solution (Key Features — Phase 2 확정 scope)

| Feature | Description | Priority | Phase 2 In-Scope? |
|---------|-------------|:--------:|:-----------------:|
| **F4. AI 시그널 (RAG + Gemini Flash)** | 지표+뉴스+시장맥락 → JSON (signal/confidence/timeframe/rationale/risks/summary_ko). 4-level 가드 + Redis 1h + Postgres 감사 로그 | **Must** | ✅ |
| **F5-a. 종목 뉴스 (Finnhub /company-news)** | 종목별 최근 7일 5~10건 수집 + Postgres 24h 캐시 | **Must** | ✅ |
| **F5-b. 뉴스 LLM 번역·요약** | Gemini: `title_ko` + `summary_ko` (3줄) + `sentiment`. 원문 토글 유지 | **Must** | ✅ |
| **RAG 파이프라인 컴포넌트** | ContextAssembler → PromptBuilder → GeminiClient → ResponseValidator | **Must** | ✅ |
| **4-level 금지용어 가드** | (1) 코드 상수 `forbidden-terms.json` (2) 프롬프트 강제 규칙 (3) LegalGuardFilter (Servlet) (4) CI grep | **Must** | ✅ |
| **`/detail` hydrate (Phase 1 scaffold → Phase 2 content)** | Phase 1 에서 `news=null`, `aiSignal=null` 이던 필드를 실제 데이터로 채움 | **Must** | ✅ |
| **NewsPanel (FE)** | 뉴스 카드 리스트 + 원문 토글 + 감성 배지 + 면책 | **Must** | ✅ |
| **AiSignalPanel (FE)** | signal 배지 + confidence 게이지 + rationale/risks 리스트 + 생성시각 + 면책 카드 내부 고정 | **Must** | ✅ |
| **비용/레이트 모니터링** | Prometheus counter (LLM 호출수/토큰/실패), `/actuator/metrics` 노출 | Should | ✅ |
| **partial response** | 뉴스만 성공·AI 실패 시에도 UI 부분 hydrate | Should | ✅ |
| **F9. 용어 사전 페이지** | RSI/MACD/볼밴 정적 설명 페이지 | Could | 🟡 (여유 시) |
| **F10. 시장 일일 브리핑** | 하루 1회 시장 요약 | Could | ❌ Phase 3 |
| **F6. 시장 대시보드** | 지수/VIX/환율/뉴스 | — | ❌ Phase 3 |
| **F7/F8. 북마크/알림** | Auth + Bookmark + Web Push | — | ❌ Phase 4 |

**Phase 2 코어 scope = F4 + F5 + RAG 인프라 + 4-level 가드.** 그 외는 명시적 out-of-scope.

### 5.7 Assumptions & Risks

| # | Assumption / Risk | Category | Confidence | Validation / Mitigation |
|---|-------------------|----------|:----------:|-------------------------|
| 1 | Gemini Flash 한국어 분석 품질이 초보에게 납득됨 | **Value** | Med | 10종목 수동 QA + 10명 파일럿 만족도 ≥ 4.0 |
| 2 | JSON 스키마 준수율이 안정적(>98%) | **Feasibility** | Med-High | `responseMimeType: application/json` 사용 + 재시도 1회 + fallback 중립 |
| 3 | Prompt injection 방어가 충분 (티커 입력 악용) | **Feasibility** | Med | 티커 regex 화이트리스트 + context 경계 마커 + 레드팀 20 케이스 |
| 4 | 4-level 가드로 "확정적 어조" 제거 실측 | **Viability** | Med | 100회 호출 샘플 금지용어 grep = 0 |
| 5 | Finnhub free `/company-news` 커버리지·속도 | **Feasibility** | Med | 대형주 우선 scope, Postgres 24h 캐시, 실패 시 뉴스 비어도 signal 독립 생성 |
| 6 | 뉴스 LLM 요약이 원문 왜곡 없음 | **Usability** | Med | 수동 스팟체크 50건 왜곡 ≤ 5%, 프롬프트에 "주체·감성 보존" 규칙 |
| 7 | 월 LLM 비용 ≤ $15 (캐시 hit 70%) | **Viability** | Med | 1주 실측 모니터링, 비용 초과 시 cache TTL 확대 |
| 8 | Gemini 분 15 RPM rate limit 방어 | **Feasibility** | Med | Bucket4j + Redis 큐, 동시 요청 직렬화 |
| 9 | "참고용" UX 각인 (신뢰도 숫자 오독 방지) | **Usability** | Med-Low | AI 카드 내부 고정 면책 + 인라인 툴팁 + summary_ko 말미 강제 문구 + Persona 3 인터뷰 |
| 10 | **(Riskiest)** 금감원 해석이 AI 시그널 출력에 우호적 유지 | **Viability** | **Low** | v1.0 Public 전 변호사 30분 리뷰, 출력 샘플 10건 리뷰 포함, 유료화 금지 유지 |
| 11 | Phase 1 에서 이관된 `/detail` scaffold 의 계약 호환 | **Feasibility** | High | Phase 1 response shape 유지, news/aiSignal 필드만 hydrate |
| 12 | Spring Boot 가상 스레드 + LLM 호출의 안정성 | **Feasibility** | Med-High | Resilience4j circuit breaker + timeout 5s |

**가장 위험한 가정 (Riskiest):** #10 (법적 해석). **가장 빠르게 검증할 것:** #2 (JSON 파싱), #4 (금지용어 제어), #6 (요약 왜곡).

### 5.8 Release Plan

| Version | Scope | Relative Timeframe | Gate |
|---------|-------|:------------------:|------|
| **v0.2-alpha** | NewsService (F5-a 수집) + NewsController `/news` + Postgres 캐시 | Week 1 | 5종목에서 뉴스 리스트 원문 표시 |
| **v0.2-beta** | 뉴스 LLM 번역·요약 (F5-b) + NewsPanel (FE) + 원문 토글 | Week 2 | 한국어 요약 파일럿 50건 왜곡 ≤ 10% |
| **v0.2-rc1** | RAG 파이프라인 (ContextAssembler/PromptBuilder/GeminiClient/ResponseValidator) + `/ai-signal` + Redis 1h | Week 3 | 10종목 수동 QA 통과, JSON 파싱 실패 < 2% |
| **v0.2-rc2** | 4-level 금지용어 가드 + 레드팀 20 케이스 + CI grep workflow | Week 4 | 금지용어 검출 0, 레드팀 방어 100% |
| **v0.2-rc3** | AiSignalPanel (FE) + 면책 UX + `/detail` hydrate 완료 + 비용/레이트 모니터링 | Week 5 | AI 시그널 종목 상세 내 렌더링, Prometheus 지표 노출 |
| **v0.2 Internal Pilot** | 10명 개발자 지인 파일럿 + 만족도 설문 + 비용 1주 실측 | Week 6 | 만족도 ≥ 4.0 / 5.0, 월 비용 추정 ≤ $15 |
| **v0.2 Public** | 긱뉴스/HN KR 재포스트 + CHANGELOG + Phase 3 우선순위 착수 | Week 7 | Launch gate 모두 통과 |

---

## 6. Risks & Mitigation (핵심 리스크 Top 3)

| Rank | Risk | Impact | Likelihood | Mitigation |
|:----:|------|--------|:----------:|-----------|
| **1** | **자본시장법 해석 변경 리스크** — AI 가 signal 값을 출력하는 순간 "객관적 요약" 경계에 섬. 금감원 유권해석이 불리하게 바뀌면 서비스 자체가 유사투자자문업 신고 대상 | 최대 (서비스 중단 가능) | Low-Med | (a) 무료 유지 + "대가 요건" 미충족 (b) 4-level 금지용어 가드 (c) 프롬프트에 "분석 정보, 자문 아님" 강제 (d) v1.0 Public 전 변호사 30분 리뷰 + 출력 샘플 10건 동봉 (e) 면책 고지 100% 커버리지 |
| **2** | **신뢰도 숫자 오독 리스크** — confidence 0.68 을 "68% 확률로 오른다" 로 읽는 사용자 (특히 Persona 3). 법적 · UX · 평판 동시 리스크 | 큼 | Med-High | (a) AI 카드에 고정 면책 + confidence 게이지 옆 인라인 툴팁 ("이는 과거 데이터 기반 판단 강도이며 예측 확률이 아닙니다") (b) summary_ko 말미 면책 문장 강제 (c) Persona 3 파일럿에서 오독 질문 포함 (d) signal 5단계 표현 ("강한 매수" 대신 "강한 상승 신호" 등 순한 표현 검토 — A/B 테스트 대상) |
| **3** | **LLM 품질·비용·가용성 동시 리스크** — Gemini 무료 티어 폐지/rate limit 강화, JSON 파싱 실패 급증, 한국어 품질 저하, 월 비용 $15 초과 | 중 (복구 가능하나 Phase 2 지연) | Med | (a) `LlmClient` 인터페이스로 GPT-4o-mini 교체 경로 확보 (b) Bucket4j + Redis 큐로 rate limit 방어 (c) Redis 1h + Postgres 24h 캐시 적극 활용 (hit ≥ 70%) (d) 실패 시 재시도 1회 후 중립 fallback (e) Prometheus 비용 대시보드 주 1회 리뷰 |

**기타 주요 리스크** (상세는 §5.7 참조): 뉴스 왜곡(#6), prompt injection(#3), Finnhub 커버리지(#5), rate limit(#8).

---

## 7. Legal & Compliance (Phase 2 필수 - 강화판)

Phase 2 는 Phase 1 대비 법적 노출이 질적으로 다름 (AI 판단 출력). 아래 항목은 **v0.2 Public Launch 전 필수**:

### 7.1 4-Level 금지 용어 가드 (코드 레벨 강제)

| Level | 구현 | 검증 |
|-------|------|------|
| **L1. 코드 상수** | `apps/api/src/main/resources/forbidden-terms.json` | 단위 테스트로 목록 커버리지 확인 |
| **L2. 프롬프트 규칙** | 시스템 프롬프트에 "사세요/파세요/매수 권유 금지, 가능성·경향 어조 사용" 명시 | 100회 샘플 호출 → 출력 grep 0건 |
| **L3. ResponseValidator (Servlet/Service)** | LLM 응답에서 금지 용어 검출 시 재시도 1회, 재실패 시 중립 처리 + 감사 로그 | `@SpringBootTest` 통합 테스트 |
| **L4. CI grep** | `.github/workflows/forbidden-terms.yml` — 코드·UI 텍스트·프롬프트 템플릿 grep | PR 차단 정책 |

### 7.2 고정 면책 노출 의무

- **AI 시그널 카드 내부**: "본 AI 분석은 공개된 시장 데이터와 뉴스를 기반으로 한 알고리즘 출력이며, 전문 금융 자문이 아닙니다."
- **뉴스 카드 하단**: "뉴스 요약은 AI 에 의해 자동 생성되며 원문과 차이가 있을 수 있습니다. 원문 확인을 권장합니다."
- **confidence 숫자 옆 인라인 툴팁**: "신뢰도는 알고리즘의 판단 강도이며 가격 상승/하락 확률이 아닙니다."
- **summary_ko 말미**: LLM 프롬프트에 "본 분석은 투자 자문이 아닙니다. 투자 판단과 책임은 사용자 본인에게 있습니다." 자동 삽입 규칙
- Phase 1 의 `DisclaimerBanner` + `DisclaimerFooter` 전역 유지

### 7.3 저작권 가드

- 뉴스 원문 **전문 재게재 금지** — Postgres 에는 헤드라인·요약·링크만 저장
- 원문 링크 필수 노출 (`NewsItem.sourceUrl`)
- 요약은 "2차 저작물 (LLM 가공)" 포지션 — 법적 안전 여지

### 7.4 Launch Gate (법적)

- [ ] 4-level 가드 4/4 구현 및 CI 통과
- [ ] 레드팀 20 케이스 전부 방어 (prompt injection + 금지 용어 유도)
- [ ] LLM 출력 샘플 10건 변호사 30분 리뷰 (v1.0 Public 전)
- [ ] Finnhub/TwelveData TOS 재검토 (뉴스 재가공 허용 여부)
- [ ] 금지 용어 grep = 0 (PR 차단 정책 활성)

---

## 8. Next Steps

1. **`/pdca plan phase2-rag-pipeline`** — 이 PRD 를 자동 참조하여 Plan 문서 작성
   - v0.2-alpha ~ v0.2 Public 세분화
   - Phase 1 `/detail` scaffold → Phase 2 hydrate 경로 명시
   - RAG 컴포넌트 간 계약 (DTO / 예외 / 캐시 키) 초안
2. **병행: MVP Phase 1 후속 조치 소진** (MVP report §7.1)
   - `m-2`: FE `SearchHit.exchange: string | null` 타입 보정
   - `m-1`: `Quote.volume` Twelve Data hydrate 결정
   - Design drift D-1~D-5 commit
3. **법적 사전 준비** (Phase 2 Launch Gate 대비)
   - `forbidden-terms.json` 초안 목록 확정
   - 변호사 30분 리뷰 미팅 D+30 스케줄링
4. **비용 모니터링 초기 셋업**
   - Prometheus counter + Grafana (또는 `/actuator/metrics`) 대시보드

---

## Attribution

This PRD was generated by **bkit PM single-session mode (pm-lead synthesis)**.
Frameworks based on [pm-skills](https://github.com/phuryn/pm-skills) by Pawel Huryn (MIT License).

- **Opportunity Solution Tree** — Teresa Torres, *Continuous Discovery Habits*
- **Opportunity Scoring** — Anthony Ulwick, *Outcome-Driven Innovation*
- **JTBD 6-Part Value Proposition** — Pawel Huryn & Aatir Abdul Rauf
- **Lean Canvas** — Ash Maurya, *Running Lean*
- **Beachhead Segment** — Geoffrey Moore, *Crossing the Chasm*

> 본 PRD 의 시장 규모 추정(Section 3.3)은 Phase 1 PRD 와 동일 수치를 계승. 런칭 전 실측 통계로 재검증 필요.
> 본 문서는 법률 자문이 아님 — 자본시장법 관련 항목은 변호사 검토 필수 (docs/planning/07-legal-compliance.md §7.6 체크리스트 참조).
