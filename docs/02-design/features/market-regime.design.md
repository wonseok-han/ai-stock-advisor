# Design: market-regime

## Executive Summary

| 관점 | 요약 |
|---|---|
| **Problem** | 대시보드가 개별 시세 위주라 "시장 전체 국면"을 볼 수단이 없고, VIX 단일 지표는 신뢰도 한계 |
| **Solution** | FRED(버핏지수·금리차·신용스프레드) + CNN(Fear&Greed) + 기존 보유 지표를 4축으로 수집·정규화해 종합 "시장 온도계" 산출, Gemini가 해석 |
| **Function UX Effect** | 대시보드 "시장 국면" 패널 — 종합 게이지 + 4축 지표 카드 + AI 한줄 해석 + 면책 |
| **Core Value** | 다축 종합 관점으로 "지금 사이클 어디쯤인가" 참고 맥락 제공 (매매 지시 아님) |

---

## 1. API Design

### 1.1 Endpoints

```
GET /api/v1/market/regime        # 공개 — 지표 + composite
GET /api/v1/market/regime/ai     # 인증(JWT) — AI 해석 (로그인 사용자 전용)
```

| 항목 | `/regime` (지표) | `/regime/ai` (AI 해석) |
|------|------------------|------------------------|
| Method | GET | GET |
| Auth | 불필요 (permitAll) | **JWT 인증 필요** |
| Cache | Redis 6시간 TTL | Redis 6시간 TTL |

> 종목 `ai-signal`과 동일하게 AI 해석만 인증 게이팅. `SecurityConfig.protectedFilterChain`의
> `securityMatcher`에 `/api/v1/market/regime/ai` 추가 (지표 엔드포인트 `/api/v1/market/regime`은 공개 유지).
> 비로그인 사용자는 지표/composite는 보고, AI 해석 영역은 로그인 유도 표시.

### 1.2 Response Schema

```json
{
  "asOf": "2026-05-29T05:00:00Z",
  "composite": { "score": 62, "label": "greed", "labelKo": "탐욕(과열 쪽)" },
  "axes": {
    "valuation": {
      "indicators": [
        { "key": "buffett", "name": "버핏지수", "value": 229.8, "unit": "%",
          "zone": "overheated", "note": "역사적 평균 대비 고평가 구간" }
      ]
    },
    "riskSentiment": {
      "indicators": [
        { "key": "fearGreed", "name": "공포·탐욕 지수", "value": 60.2, "zone": "greed",
          "prev1w": 58.9, "prev1m": 66.2, "prev1y": 64.5 },
        { "key": "creditSpread", "name": "HY 신용스프레드", "value": 2.71, "unit": "%", "zone": "calm" },
        { "key": "vix", "name": "VIX (보조)", "value": 15.7, "zone": "calm" }
      ]
    },
    "macro": {
      "indicators": [
        { "key": "yieldCurve", "name": "장단기 금리차(10Y-2Y)", "value": 0.46, "unit": "%p", "zone": "normal" }
      ]
    },
    "trendBreadth": {
      "indicators": [
        { "key": "sp500vs200ma", "name": "S&P500 vs 200일선", "value": 4.2, "unit": "%", "zone": "uptrend" }
      ]
    }
  },
  "disclaimer": "본 지표는 투자 자문이 아닌 정보 제공·참고용입니다. 투자 판단과 책임은 사용자 본인에게 있습니다."
}
```

> `zone` enum: `cheap | calm | normal | neutral | greed | overheated | fear | inverted | uptrend | downtrend` (지표별 적용)

#### `GET /api/v1/market/regime/ai` 응답 (인증 전용)

```json
{
  "asOf": "2026-05-29T05:00:00Z",
  "aiSummary": "버핏지수는 역사적 고평가 구간이고 신용스프레드는 안정적입니다. 다만 이는 확정적 예측이 아닌 참고 지표입니다.",
  "disclaimer": "본 해석은 투자 자문이 아닌 정보 제공·참고용입니다. 투자 판단과 책임은 사용자 본인에게 있습니다."
}
```

> AI 해석은 동일한 지표 스냅샷(`market:regime` 캐시)을 입력으로 Gemini가 생성. 비로그인 시 401 → FE는 로그인 유도.

---

## 2. 데이터 소스 & 시리즈 (검증 완료)

| 지표 | 소스 | 시리즈/엔드포인트 | 단위 | 갱신 |
|------|------|-------------------|------|------|
| 버핏지수 | FRED | `NCBEILQ027S`(백만$) ÷ `GDP`(십억$) × 100 | % | 분기 |
| 장단기 금리차 | FRED | `T10Y2Y` | %p | 일 |
| 신용스프레드 | FRED | `BAMLH0A0HYM2` | % | 일 |
| Fear & Greed | CNN | `production.dataviz.cnn.io/index/fearandgreed/graphdata` (UA+Referer) | 0~100 | 일 |
| VIX(보조) | 기존 | MarketOverview | pt | 실시간 |
| S&P500 vs 200일선 | 기존 | 일봉 캔들 계산 (`.INX`/`^GSPC`) | % | 일 |

> 버핏지수 단위 변환: `NCBEILQ027S / 1000`(→십억$) `/ GDP × 100`.
> FRED `FRED_API_KEY` 필요. CNN은 UA + `Referer: https://www.cnn.com/markets/fear-and-greed`.

---

## 3. zone 정규화 임계값

| 지표 | cheap/fear | normal/neutral | overheated/greed |
|------|-----------|----------------|------------------|
| 버핏지수 | <100 | 100~150 | >150 (>200 강과열) |
| 금리차(10Y-2Y) | <0 `inverted`(침체신호) | 0~0.5 | >0.5 `normal` |
| 신용스프레드 | <3 `calm` | 3~5 | >5 `stress`(공포) |
| Fear&Greed | 0~25 극공포 / 25~45 공포 | 45~55 | 55~75 탐욕 / 75~100 극탐욕 |
| VIX | <15 calm | 15~25 | >25 불안 |
| S&P vs 200일선 | <0 downtrend | ~0 | >0 uptrend |

**composite score**: 각 지표를 0(공포/저평가)~100(과열/고평가)으로 정규화 후 **축별 평균 → 4축 균등 가중 평균**. (가중치는 상수로 분리해 조정 가능)

---

## 4. BE 컴포넌트

```
com.nowini.market
├── infra
│   ├── FredClient            # FRED series observations (key 인증)
│   ├── FredProperties        # app.external.fred.* (api-key)
│   └── CnnFearGreedClient    # CNN dataviz (UA+Referer, key 불필요)
└── service
    └── MarketRegimeService   # 수집 → 정규화 → composite → AI 해석, Redis 캐시
└── web
    └── MarketRegimeController # GET /regime(공개), GET /regime/ai(인증)
```

- `FredClient.latestValue(seriesId)` → 최신 observation (WebClient, 기존 FmpClient 패턴)
- `MarketRegimeService`: 각 소스 병렬 수집 → zone 정규화 → composite → `MarketRegimeResponse`(지표). AI 해석은 별도 메서드로 분리해 `/regime/ai`에서만 호출
- 부분 실패 허용: 일부 지표 실패해도 나머지로 composite 산출 (null 지표는 응답에서 제외/표기)
- 캐시: `market:regime` 6시간 TTL (지표), `market:regime:ai` 6시간 TTL (AI 해석)
- **인증**: `SecurityConfig.protectedFilterChain`의 `securityMatcher`에 `/api/v1/market/regime/ai` 추가 (기존 `ai-signal`과 동일 패턴). `/api/v1/market/regime`은 publicFilterChain(permitAll) 유지

### AI 해석 프롬프트 (면책 프레임)
- 입력: 4축 지표값 + zone
- 출력: 한국어 1~2문장 **관점 제시** (단정 금지). "~를 시사하나 확정적 예측이 아니며, 판단은 사용자" 톤 강제
- 실패 시 aiSummary는 null (지표는 그대로 노출)

---

## 5. FE 컴포넌트

```
apps/web/src/features/market-dashboard
└── components/market-regime
    ├── market-regime-panel.tsx     # 종합 게이지 + 4축 + AI 요약 + 면책
    ├── regime-gauge.tsx            # composite 0~100 게이지 (공포↔과열)
    └── regime-axis-card.tsx        # 축별 지표 카드
```

- React Query로 `/api/v1/market/regime`(지표, 공개) + 로그인 시 `/api/v1/market/regime/ai`(AI 해석)
- 각 지표 zone별 색상(공포=파랑/과열=빨강 등), 1주/1달 변화 표기
- **AI 요약은 로그인 사용자만 표시** (비로그인 시 로그인 유도 카드 — 종목 AI 패턴 동일)
- **하단 고정 면책 문구** + "참고" 배지

---

## 6. 구현 순서

1. `FredClient` + `FredProperties` + `CnnFearGreedClient` (+ `.env.example`, application.yml)
2. zone 정규화 + composite 산출 로직 (`MarketRegimeService`)
3. `MarketRegimeController` + `MarketRegimeResponse` DTO
4. Gemini AI 해석 통합 (기존 `LlmClient` 재활용)
5. FE 패널 (게이지 + 축 카드 + 면책)
6. 캐시/부분실패/면책 문구 검수

---

## 7. 면책 (필수)

- 모든 출력은 **정보 제공·참고용**, 매수/매도/현금화 등 지시 금지 (`no-investment-advice`)
- **금칙어 회피**: 응답에 `LegalGuardFilter` 금칙어(`투자 권유`, `매수 추천`, `추천드립니다`, `사세요` 등 `legal/forbidden-terms.json`)가 있으면 fallback으로 차단됨. 면책 문구는 "투자 자문이 아닌"처럼 금칙어 비포함 표현 사용. AI 해석 프롬프트에도 금칙어 금지 명시
- composite·AI 요약에 반드시 면책 문구 동반
- AI 해석은 관점 제시(단정 금지)
- 참조: `docs/planning/07-legal-compliance.md`, `docs/01-plan/features/market-regime.plan.md`
