# mvp - Product Requirements Document

> **Date**: 2026-04-13
> **Author**: wonseok-han
> **Method**: bkit PM Agent Team (single-session, CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS unset)
> **Status**: Draft
> **Based on**: [pm-skills](https://github.com/phuryn/pm-skills) by Pawel Huryn (MIT)

---

## Executive Summary

| Perspective | Content |
|-------------|---------|
| **Problem** | 미국 주식에 관심 있는 한국어 라이트 투자자는 **영어 뉴스 · 기술지표 · 시장 맥락 · 뉴스**가 도구마다 분산돼 있어 "지금 이 종목 분위기는 어떻지?"를 3분 안에 판단하기 어렵다. |
| **Solution** | 티커 하나로 한국어 통합 대시보드(차트 + 지표 해설 + 뉴스 요약 + AI 시그널 + 시장 맥락)를 제공하는 무료 참고 도구. **"투자 자문이 아닌 분석 도구"** 로 법적 포지셔닝. |
| **Target User** | 해외 주식 1년 이내 한국어 라이트 투자자 (**Beachhead: IT 업계 25~35세 개발자** — 학습 문화 + 소셜 전파력) |
| **Core Value** | "미국 주식을 한국어로 3분 안에 이해한다" — 영어·용어·분산 세 장벽을 한꺼번에 낮춤. 월 운영비 $15 이하로 1인 개발자가 지속 가능. |

---

## 1. Opportunity Discovery

### 1.1 Desired Outcome

> **"해외 주식을 시작한 한국어 라이트 투자자가 종목 의사결정 직전 단계에서 가장 먼저 여는 한국어 분석 도구"가 된다.**

측정 지표 (Activation proxy):
- **검색 → 종목 상세 30초 이상 체류율** ≥ 40% (3개월 내)
- **종목 상세 내 지표 탭 1회 이상 클릭률** ≥ 50%
- **MAU** 1,000명 이상 (3개월 내), 10,000명 (12개월 내)

### 1.2 Opportunity Solution Tree

```
Outcome: 해외 주식 의사결정 직전 1순위로 여는 한국어 분석 도구가 된다
├── Opportunity 1: 영어 + 전문용어 장벽으로 정보 소비가 단절됨
│   ├── Solution A: LLM 뉴스 한국어 번역/요약 (F5)
│   ├── Solution B: 기술 지표 카드에 초보용 해설 툴팁 (F3)
│   └── Solution C: AI 시그널의 rationale/risks 를 한국어로 구조화 (F4)
├── Opportunity 2: 차트·뉴스·시장지표가 앱/사이트에 분산되어 맥락 조립 비용 큼
│   ├── Solution D: 종목 상세 단일 페이지에 차트+지표+뉴스+AI+시장맥락 통합 (F1-F5)
│   └── Solution E: 시장 대시보드로 오늘 분위기 한눈에 (F6)
├── Opportunity 3: "지금 이 종목/시장 분위기"를 직관적으로 판단할 도구 부재
│   ├── Solution F: AI가 기술지표+뉴스+시장맥락을 종합해 signal/confidence/rationale/risks JSON 반환 (F4 핵심)
│   └── Solution G: 시그널 변화 기반 푸시 알림 (Phase 4 - post-MVP)
└── Opportunity 4: 유튜브·블로그 일방 편향 콘텐츠는 시의성·객관성 낮음
    ├── Solution H: 실시간 데이터 기반 + Redis 캐시로 분 단위 최근성
    └── Solution I: 프롬프트에 "확정적 어조 금지, 근거 기반 판단" 규칙 강제
```

### 1.3 Prioritized Opportunities

Importance × (1 - Satisfaction) 방식 (Opportunity Score, Ulwick 기반):

| # | Opportunity | Importance | Satisfaction | Score |
|---|------------|:----------:|:------------:|:-----:|
| 1 | Opportunity 3: AI로 "지금 분위기 판단" | 0.9 | 0.2 | **0.72** |
| 2 | Opportunity 1: 영어/용어 장벽 해소 | 0.85 | 0.3 | **0.60** |
| 3 | Opportunity 2: 정보 통합 대시보드 | 0.80 | 0.35 | **0.52** |
| 4 | Opportunity 4: 시의성·객관성 보강 | 0.60 | 0.40 | **0.36** |

→ **F4 (AI 시그널) 가 단일 킬러 피처**. F3/F5 (해설/번역) 가 "왜 이 판단인지" 설명하는 **지지 피처**. F6 (시장 대시보드) 가 맥락 제공.

### 1.4 Recommended Experiments

| # | Tests Assumption | Method | Success Criteria |
|---|-----------------|--------|------------------|
| 1 | AI 시그널의 rationale/risks가 초보에게 납득됨 | 10명 파일럿 세션 녹화 + 만족도 5점 척도 | 평균 ≥ 4.0 / 5.0 |
| 2 | 통합 대시보드가 기존 도구 2~3개를 대체함 | 사용 전후 "주로 쓰던 도구" 인터뷰 | 70%+ "다른 앱 덜 씀" |
| 3 | 면책 고지가 UX를 해치지 않음 | 면책 크기/위치 A/B 배너 vs 인라인 | 상세 페이지 CTR 변화 ≤ 5% |
| 4 | Gemini Flash 한국어 품질이 유료 모델(GPT-4o-mini) 대비 충분 | 동일 입력으로 30개 종목 blind 평가 | 품질 차이 ≤ 0.5점 / 5.0점 |
| 5 | 뉴스 LLM 요약이 원문 왜곡을 일으키지 않음 | 수동 스팟체크 50건 | 의미 왜곡 ≤ 5% |

---

## 2. Value Proposition & Strategy

### 2.1 JTBD Value Proposition (6-Part)

| Part | Content |
|------|---------|
| **Who** | 미국 주식을 직접 매매하는 한국어 라이트 투자자 (해외주식 계좌 보유, 일일 10~30분 정보 탐색, 영어 불편) |
| **Why** | 종목/시장 판단에 필요한 정보가 영어 + 여러 도구에 분산되어 **소화 비용이 매매 결정 비용보다 크다** |
| **What Before** | 증권사 앱(가격/체결만) → 인베스팅닷컴/Yahoo(영어, 복잡) → 유튜브 종목 분석(편향) → 카톡/텔레그램 방(신뢰도↓) 를 **번갈아** 돌아봄 |
| **How** | 티커/회사명 하나로 한국어 통합 대시보드: 차트·지표 해설·뉴스 요약·AI 시그널·시장 맥락이 단일 화면에 |
| **What After** | 3~5분 내에 "이 종목/시장의 현재 분위기"를 스스로 판단, 이후 **증권사 앱에서 매매만** 수행 |
| **Alternatives** | Yahoo/SA(영어), 인베스팅(복잡/광고), 증권사 리서치(해외 빈약), 유튜브(편향/시의성) — 우리는 **한국어 × 초보 친화 × AI 종합 × 무료** 조합 |

**Value Proposition Statement**:
> "해외 주식 초보자가 미국 주식의 오늘 분위기를 **3분 안에 한국어로** 파악하고 스스로 판단하게 한다."

### 2.2 Lean Canvas

| Section | Content |
|---------|---------|
| **Problem** | 1) 영어 뉴스·전문용어 장벽 2) 차트·뉴스·지수 분산 3) "지금 분위기" 판단 도구 부재 |
| **Solution** | 1) LLM 한국어 번역·요약 2) 통합 대시보드 3) AI 시그널 + 근거·리스크 |
| **UVP** | 미국 주식, 3분 한국어 대시보드 — 참고용 AI 시그널 무료 |
| **Unfair Advantage** | (a) 법적 "분석 도구" 포지셔닝 ("매수/매도 권유 금지" 자체 규칙) (b) 초보 타겟 용어 가이드라인 (c) RAG 기반 서버 측 컨텍스트 조립 → 할루시네이션 최소화 (d) 1인 개발로 빠른 반복 |
| **Customer Segments** | 25~40세 한국어 라이트 투자자 (해외주식 1년 미만 중심) |
| **Channels** | 개발자 커뮤니티 (긱뉴스/디스콰이엇/HN KR), 네이버 블로그 종목 SEO, X/Twitter 개발자 타임라인 |
| **Revenue Streams** | **없음** (무료 유지 — 유료화 시 자본시장법상 "대가 요건" 충족되어 유사투자자문업 신고 필요) |
| **Cost Structure** | 호스팅 $0 (Vercel/Fly Free) + LLM $5~10/월 + 도메인 $1/월 ≈ **$6~11/월** |
| **Key Metrics** | WAU, 종목 상세 30s+ 체류율, 검색→체류 전환율, LLM 캐시 hit ratio, 월 운영비 |

---

## 3. Market Research

### 3.1 User Personas

#### Persona 1: "초보 지후" (해외주식 신규 진입자)

| Attribute | Details |
|-----------|---------|
| **Demographics** | 29세, IT 스타트업 주니어 엔지니어, 연봉 4,500만원, 서울 거주 |
| **Primary JTBD** | 해외주식 계좌 개설 직후, 뉴스에서 본 종목을 사기 전 "지금이 적기인가" 확신을 얻고 싶음 |
| **Pain Points** | 1) 영어 뉴스 피곤 2) RSI/MACD 용어 모름 3) Yahoo·인베스팅·유튜브 돌려보느라 시간 낭비 |
| **Desired Gains** | 1) 3분 내 파악 2) 한국어 3) "이게 뭔 뜻이냐"에 답하는 해설 |
| **Unexpected Insight** | 기술 지표보다 "최근 뉴스 요약"을 훨씬 자주 봄 → **뉴스 중심 사고**. 지표는 "뉴스의 보조 신호"로 소비 |
| **Product Fit** | F5 한국어 뉴스 요약 + F3 지표 툴팁 + F4 AI가 "종합 정리" 로 접점 제공 |

#### Persona 2: "자기주도 주희" (포트폴리오 트래커)

| Attribute | Details |
|-----------|---------|
| **Demographics** | 34세, 프리랜서 UX 디자이너, 연 3,000만원, 해외주식 2년차, 포트폴리오 5~10종목 |
| **Primary JTBD** | 매일 보유 종목 뉴스/지수 확인. 시간은 오래 걸리고, 큰 이벤트(FOMC, CPI) 놓치기 쉬움 |
| **Pain Points** | 1) 도구 3~4개 돌려보는 피로 2) 뉴스 한영 번역 수고 3) 시장 전체 맥락 별도 수집 |
| **Desired Gains** | 1) 통합 뷰 2) 시장 지수/환율/VIX 한눈에 3) 주요 이벤트 고지 |
| **Unexpected Insight** | **의사결정은 스스로 하지만, "내 판단을 검증할 2차 소스"를 원함** → AI를 "권위"가 아닌 "second opinion"으로 소비 |
| **Product Fit** | F6 시장 대시보드 + F4 AI 시그널 = second opinion, F7/F8 (post-MVP)로 재방문 유도 |

#### Persona 3: "호기심 현우" (가끔 탐색자)

| Attribute | Details |
|-----------|---------|
| **Demographics** | 41세, 중소기업 팀장, 연 6,500만원, 해외주식은 유튜브 따라 가끔 소액 매매 |
| **Primary JTBD** | 뉴스·커뮤니티에서 본 종목(NVDA, TSLA 등) 을 검색해 "분위기만" 확인하고 싶음. 깊이 공부할 시간 없음 |
| **Pain Points** | 1) 증권앱은 가격만 2) 유튜브는 시의성↓ + 편향 3) 영어 사이트는 부담 |
| **Desired Gains** | 1) 검색 즉시 요약 2) "좋은가 나쁜가" 직관 신호 3) "참고용" 이라는 안심 메시지 |
| **Unexpected Insight** | **시그널/신뢰도 숫자를 "확정"으로 오독할 위험이 가장 큰 세그먼트** → 면책·해설 UX가 특히 중요 |
| **Product Fit** | F1 검색 → F4 시그널 → 면책 UX 설계로 "참고용" 강조 필수 |

### 3.2 Competitive Landscape

| Competitor | Strengths | Weaknesses | Our Opportunity |
|-----------|-----------|------------|-----------------|
| **Yahoo Finance** | 데이터 폭·무료·글로벌 표준, 차트/뉴스/재무 풍부 | 영어, UI 정보 폭포식, 초보엔 과도, AI 해석 없음 | 한국어 + 초보 친화 + AI 해석 레이어 |
| **Investing.com (한국어판)** | 한국어 지원, 뉴스 풍부, 실시간 시세 | UI 복잡, 광고 많음, AI 해석 없음, 초보엔 여전히 전문가 UX | 간결한 통합 뷰 + AI 시그널 + 광고 없음 |
| **증권사 앱 (토스증권/키움/삼성)** | 계좌 연동, 신뢰도, 실시간 체결, 한국어 | 해외 주식 분석 빈약, 뉴스 약함, AI 분석 없음 | 증권사와 "분석 레이어"로 **보완** (경쟁 아닌 병행) |
| **유튜브 종목 분석 채널** | 공감·해설·스토리텔링, 한국어 | 시의성↓ (녹화→업로드 지연), 편향, 검색 어려움, 구독 피로 | 실시간 데이터 기반 객관 분석 |
| **Seeking Alpha / MarketBeat** | 전문가 레포트, 심층 분석, 커뮤니티 | 영어, 유료화 공격적 ($19~/월), 초보엔 깊이가 과도 | 초보용 요약·해석·한국어·무료 |

**Differentiation Strategy**:
> **"한국어 × 초보 친화 × AI 종합 시그널 × 무료"** 4개 교집합은 현재 국내 시장에 **비어 있다**. 증권사는 데이터·체결에 강하지만 분석·해석 레이어가 약하고, 유튜브·블로그는 편향·시의성이 약함. 우리는 **증권사와 경쟁하지 않고 보완**한다 — 사용자는 우리 앱에서 "이해"하고, 증권사 앱에서 "매매"한다.

### 3.3 Market Sizing

국내 해외주식 투자자 추정 근거:
- 한국예탁결제원 외화증권 보관 인구 최근 공표치 (~700만명 수준)
- 이 중 "한국어 불편함 + 라이트 유저(해외주식 1년 이내)" 비율 ~30% 추정 = 약 200만명
- 분석 도구 수용 의향: 무료라는 전제 하에 50% = 100만명

| Metric | Current Estimate | 3-Year Projection |
|--------|-----------------|-------------------|
| **TAM** | 국내 해외주식 투자자 × 무료 도구 수용 의향 = **약 420만 MAU 잠재** (700만 × 60%) | 해외주식 붐 유지 시 **500만 MAU** |
| **SAM** | 한국어 라이트 유저 × 분석 도구 의향 = **약 100만 MAU 잠재** (200만 × 50%) | **150만 MAU** (세그먼트 자연 증가) |
| **SOM (3년)** | 콘텐츠 SEO + 커뮤니티 유입 기반, MVP 1년차 **월 1~3만 MAU**, 3년차 **10~15만 MAU** | **10~15만 MAU** |

**Key Assumptions**:
1. 국내 해외주식 보유 인구의 성장세가 향후 3년간 완만하게라도 유지된다
2. 무료 AI 분석 도구에 대한 "참고용" 수용성이 1인 개발 프로젝트에게도 존재한다 (즉, 신뢰성 = 유명 브랜드가 아니어도 됨)
3. 금감원의 유사투자자문업 해석이 "무료 + 일반화된 분석"에 우호적으로 유지된다 (Phase 4 유료화 시 재평가 필수)
4. Gemini Flash (또는 GPT-4o-mini) 수준의 LLM 무료/저가 티어가 3년 내 폐지되지 않는다

---

## 4. Go-To-Market

### 4.1 Beachhead Segment

4개 세그먼트 후보 평가 (1-5점):

| Criteria | 해외주식 신규 (<1년) | **IT 25-35 개발자 (신규)** | 유튜브 종목 시청자 | 재테크 블로거/커뮤니티 |
|----------|:----:|:----:|:----:|:----:|
| Burning Pain | 5 | 4 | 3 | 3 |
| Willingness to Pay (추천 전파력) | 2 | 3 | 2 | 3 |
| Winnable Share | 4 | **5** | 3 | 4 |
| Referral Potential | 3 | **5** | 4 | 5 |
| **Total** | 14 | **17** | 12 | 15 |

**Primary Beachhead: IT 업계 25~35세 개발자 신규 해외주식 투자자**

선정 근거 (4.1 Evidence):
- (a) **학습·분석 도구 문화 친숙** → AI 시그널에 대한 "참고용" 해석을 가장 정확히 함
- (b) **소셜 전파력 + 피드백 품질** → 개발자 커뮤니티(긱뉴스, HN KR, 디스콰이엇)에서 입소문 확산 빠름
- (c) **1인 개발 프로젝트에 대한 신뢰도 + 공감대** (유명 브랜드 아닌 개인 프로젝트도 기꺼이 사용)
- (d) **채널-프로덕트 매칭**: 우리 배포 채널이 곧 타겟 세그먼트 → CAC 거의 $0
- (e) **Bug report 품질** 높음 → 빠른 MVP 개선 사이클 가능

**90-Day Acquisition Plan**:

| Day | Action |
|---|---|
| D-30 ~ D0 | MVP(Phase 1~3) 개발 + **기획 문서 Public 공개** (build-in-public 효과) |
| D0 | v1.0 배포 + 긱뉴스/디스콰이엇/HN KR "개인 프로젝트 공개" 포스트 |
| D0 ~ D30 | 개발자 X/Twitter/블로그 **자발적 언급 모니터링** + 피드백 수렴 |
| D30 ~ D60 | 인기 미국 종목 TOP 10 (AAPL/TSLA/NVDA/MSFT/GOOGL/META/AMZN/AMD/PLTR/TSM) **한국어 분석 페이지 SEO 최적화** (서버-사이드 렌더링) |
| D60 ~ D90 | 활성 사용자 5~10명 심층 인터뷰 → Phase 4 (북마크/알림) 스펙 확정 |

### 4.2 GTM Strategy

| Element | Details |
|---------|---------|
| **Channels** | 1) 개발자 커뮤니티 (긱뉴스 · HackerNews KR · 디스콰이엇 · GeekNews) 2) X/Twitter 개발자 타임라인 3) 네이버 블로그 종목 분석 페이지 SEO 4) GitHub Public repo README 유입 |
| **Messaging** | 메인: "미국 주식, 3분 한국어 대시보드 — 참고용 AI 시그널, 무료" / 서브: "투자 자문 아님, 초보 친화 분석 도구" |
| **Success Metrics (90일)** | WAU 200, 종목 상세 30초+ 체류율 40%, 검색→체류 전환 50%, 커뮤니티 포스트 조회수 5,000+ |
| **Launch Timeline** | **Pre-launch** (D-30~0): 기획 문서 공개, build-in-public. **Launch** (D0): 배포 + 커뮤니티 포스팅. **Post-launch** (D0+): 사용자 피드백 기반 Phase 4 우선순위 확정 |

---

## 5. Product Requirements (PRD)

### 5.1 Summary

**초보 한국어 라이트 투자자가 미국 주식을 3분 안에 이해하도록 돕는 무료 통합 참고 대시보드.** 종목 상세 페이지(차트·지표 해설·뉴스 요약·AI 시그널)와 시장 대시보드(주요 지수·VIX·환율·시장 뉴스)를 단일 웹앱으로 제공한다. **"투자 자문이 아닌 분석 도구"** 로 법적 포지셔닝하여 유사투자자문업 신고 없이 운영하며, 월 운영비 $15 이하를 목표로 Gemini Flash 기반 RAG 아키텍처를 채택한다.

### 5.2 Background & Context

**왜 지금인가?** 다음 세 변화가 2024~2025년에 수렴하며 "무료 + 한국어 + AI 해석" 조합이 **기술적 · 비용적 · 법적으로** 가능해졌다.

1. **LLM 비용 급락** — Gemini 1.5 Flash 무료 티어(일 1M 토큰)와 $0.075/1M input 유료 티어로 월 10K회 한국어 분석이 $5 수준
2. **차트 라이브러리 오픈소스화** — TradingView Lightweight Charts Apache 2.0 (상업적 사용 무료)
3. **국내 해외주식 붐 후 2~3년차** — 초보에서 자기주도로 넘어가는 200만+ 세그먼트가 "도구 공백"에 도달

**무엇이 가능해졌나?**
- 1인 개발자가 월 $15 이하로 한국어 미국 주식 분석 서비스를 지속 가능하게 운영
- 자체 모델 없이 "서버가 구조화 데이터 조립 → LLM이 판단자" 구조로 할루시네이션 최소화
- 무료 유지로 자본시장법 "대가" 요건 회피 → 유사투자자문업 신고 불필요 (A안 채택)

### 5.3 Objectives & Key Results

| Objective | Key Result | Target (3개월) | Target (12개월) |
|-----------|-----------|:---:|:---:|
| **O1. 한국어 라이트 투자자에게 유의미한 참고 도구로 검증** | MAU | 1,000 | 10,000 |
| | 종목 상세 평균 체류시간 | ≥ 60s | ≥ 90s |
| | 검색→상세 체류 전환율 | ≥ 70% | ≥ 75% |
| | 재방문율 (Week-4) | ≥ 20% | ≥ 35% |
| **O2. 운영 비용 통제** | 월 총 운영비 | ≤ $15 | ≤ $30 (MAU 10K 기준) |
| | LLM 캐시 hit ratio | ≥ 70% | ≥ 80% |
| **O3. 법적 리스크 제로 유지** | 면책 고지 페이지 노출 커버리지 | 100% | 100% |
| | 금지 용어 grep 검사 fail count (CI) | 0 | 0 |
| | LLM 응답 "투자 자문" 키워드 검출 | 0 | 0 |

### 5.4 Market Segments

세그먼트는 **JTBD (왜 이 도구를 쓰는가)** 로 정의하고 데모그래픽은 보조.

- **Primary (Beachhead):** 해외주식 1년 미만 + IT 업계 25~35세 개발자 → 지후 페르소나
- **Secondary:** 포트폴리오 5~10종목 보유 2년차 자기주도 투자자 (세컨드 오피니언 수요) → 주희 페르소나
- **Tertiary:** 뉴스/커뮤니티 보고 검색해서 찾아오는 일회성 탐색자 (SEO 유입) → 현우 페르소나

### 5.5 Value Propositions

- **Customer Jobs**: (a) 매매 직전 분위기 확인 (b) 보유 포트폴리오 일일 모니터링 (c) 뉴스에서 본 종목 분위기 스캔
- **Gains Created**: 한국어 / 3분 내 / 통합 뷰 / "참고용" 안심감 / 무료
- **Pains Relieved**: 영어 장벽 / 정보 분산 / 용어 장벽 / 유튜브 편향·시의성 / 유료 도구 부담
- **Reference**: Section 2.1 JTBD 6-Part

### 5.6 Solution (Key Features)

| Feature | Description | Priority | Addresses | Phase |
|---------|-------------|:--------:|-----------|:-----:|
| **F1. 종목 검색 + 기본정보** | 티커/회사명 입력 → Finnhub `/search` + `/profile2` | **Must** | 진입점 | 1 |
| **F2. 차트 (TradingView Lightweight)** | 캔들 + MA5/20/60 + 볼린저밴드 + 거래량 (1D/1W/1M/3M/1Y/5Y) | **Must** | Opp 2 | 1 |
| **F3. 기술 지표 카드 (ta4j)** | RSI / MACD / 볼밴 / MA + 초보 해설 툴팁 | **Must** | Opp 1 (용어) | 1 |
| **F4. AI 시그널 (Gemini Flash)** | 지표+뉴스+시장 → JSON (`signal`, `confidence`, `rationale`, `risks`, `summary_ko`) | **Must** | Opp 3 (핵심) | 2 |
| **F5. 종목/시장 뉴스 (LLM 번역)** | Finnhub `/company-news` + Gemini 한국어 요약 | **Must** | Opp 1 (영어) | 1~2 |
| **F6. 시장 대시보드** | 지수 · VIX · USD/KRW · 10Y 금리 · 시장 뉴스 · 인기/급등락 | **Must** | Opp 2 (맥락) | 3 |
| **F7. 북마크** | 로그인 유저 저장 (Supabase Auth + Spring JWT) | Should | 재방문 유도 | 4 |
| **F8. 푸시 알림** | Web Push API + 스케줄러 (가격/뉴스/시그널 변화) | Could | 재방문 유도 | 4 |
| **F9. 용어 사전 페이지** | RSI/MACD/볼밴 정적 설명 | Could | Opp 1 보조 | 1~2 |
| **F10. 시장 일일 브리핑 (AI)** | 하루 1회 시장 요약 생성 | Could | Opp 3 보조 | 3+ |

**MVP 범위 = F1 ~ F6 + F9(가능 시).** F7/F8은 post-MVP (Phase 4).

### 5.7 Assumptions & Risks

| # | Assumption | Category | Confidence | Validation Method |
|---|-----------|----------|:----------:|-------------------|
| 1 | Gemini Flash 한국어 분석 품질이 초보에게 "납득 가능한" 수준 | **Value** | Med | 10명 파일럿 만족도 4.0/5, GPT-4o-mini 블라인드 비교 |
| 2 | 금감원 유사투자자문업 해석이 "무료 + 일반화된 분석"에 우호적 | **Viability** | **Low** | 변호사 30분 리뷰 (런칭 전 필수), FAQ 재독 |
| 3 | Finnhub 무료 플랜(60 req/min)이 Phase 1~3 트래픽 감당 | **Feasibility** | Med | Redis 캐시 hit ratio 70% 전제. 부하 테스트 with MAU 5K 시뮬레이션 |
| 4 | TradingView Lightweight Charts가 초보에게 어색하지 않음 | **Usability** | High | 모바일/데스크톱 QA + 5명 사용성 테스트 |
| 5 | 개발자 커뮤니티 beachhead가 실제 입소문을 일으킴 | **Value** | Med | 커뮤니티 포스트 조회수 5K+, 댓글 30+, 외부 언급 5건+ |
| 6 | 월 $15 이하 운영비가 트래픽 급증(MAU 5K) 시에도 유지 | **Viability** | Med | 트래픽 1K → 5K 구간에서 비용 모니터링 대시보드 |
| 7 | LLM prompt injection 방어가 충분 (티커 대신 악성 입력) | **Feasibility** | Med | 레드팀 테스트 20 케이스, 입력 sanitize 레이어 |
| 8 | 뉴스 "요약 + 원문 링크" 조합이 저작권 이슈 없음 | **Viability** | Med | 판례 스캔, 원문 전문 재게재 금지 코드 레벨 강제 |
| 9 | Yahoo Finance 비공식 fallback이 실제 차단되지 않음 | **Feasibility** | Low | UA 제한, 분당 30회 이하, Finnhub 우선 |
| 10 | 한국어 라이트 유저의 AI 출력 오독(숫자를 확정으로 읽기) 이 관리 가능 | **Usability** | Med | 면책 UX A/B 테스트, 파일럿 인터뷰에서 오독 사례 수집 |

**가장 위험한 가정 (Riskiest Assumption):** #2 (법적 해석). **가장 빠르게 검증할 것:** #1 (파일럿 만족도), #5 (커뮤니티 반응).

### 5.8 Release Plan

| Version | Scope | Relative Timeframe | Gate |
|---------|-------|:------------------:|------|
| **v0.1** (Phase 1) | F1/F2/F3/F5(번역 제외) 단일 종목 파이프라인. 로그인 없음 | Week 1~4 | AAPL 검색 → 차트/지표/원문뉴스 표시 |
| **v0.2** (Phase 2) | F4 AI 시그널 + F5 LLM 번역/요약 | Week 5~7 | 10종목 수동 테스트 품질 OK |
| **v0.3** (Phase 3) | F6 시장 대시보드 완성 + 인기 종목 SEO 랜딩 | Week 8~9 | 메인 페이지가 "오늘 시장 어때?"에 답 |
| **v1.0 MVP Public** | 면책/약관/프라이버시 페이지 완성 + Public 배포 + 커뮤니티 공개 | Week 10 | 변호사 30분 리뷰, 법적 체크리스트 100% |
| **v1.1** (Phase 4) | F7/F8 북마크 + 알림 + Supabase Auth | Week 11~14 | 가입→북마크→알림 수신 E2E |
| **v1.2+** | 사용자 피드백 기반 우선순위 (용어사전·시장브리핑·모바일앱·추가지표) | Week 15+ | - |

---

## Next Steps

1. **`/pdca plan mvp`** — 이 PRD를 자동 참조하여 Plan 문서 작성 (MVP 범위 세분화 + 실행 가능 태스크 분해)
2. **`/pdca plan phase-1-core`** — Phase 1(단일 종목 파이프라인) 부터 좁게 계획 (권장: feature 단위로 작게 쪼개기)
3. **법적 검증 착수** — 리스크 #2 해소를 위해 v1.0 MVP Public 전 변호사 30분 리뷰 스케줄링

---

## Attribution

This PRD was generated by **bkit PM single-session mode** (Agent Teams env unset).
Frameworks based on [pm-skills](https://github.com/phuryn/pm-skills) by Pawel Huryn (MIT License).

- **Opportunity Solution Tree** — Teresa Torres, *Continuous Discovery Habits*
- **Opportunity Scoring** — Anthony Ulwick, *Outcome-Driven Innovation*
- **JTBD 6-Part Value Proposition** — Pawel Huryn & Aatir Abdul Rauf
- **Lean Canvas** — Ash Maurya, *Running Lean*
- **Beachhead Segment** — Geoffrey Moore, *Crossing the Chasm*

> ⚠️ 본 PRD의 시장 규모 추정(Section 3.3)은 예탁결제원 및 금융투자협회 공표치를 근거로 하지만 **2026-04-13 시점의 공식 실측이 아님**. 런칭 전 실제 통계로 재검증 필요.
