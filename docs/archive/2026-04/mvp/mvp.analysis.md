# MVP Phase 1 Gap Analysis

> **Date**: 2026-04-14
> **Scope**: Phase 1 MVP (search / profile / quote / candles / indicators)
> **Feature**: mvp
> **Design doc**: `docs/02-design/features/mvp.design.md`

## 1. 요약

- **분석 범위**: 설계 §4.1 Phase 컬럼 기준 Phase 1 항목 한정.
  Phase 2(news/ai-signal/detail content)와 Phase 4(auth/bookmark/notification) 는 의도적 미구현으로 감점 제외.
- **Phase 1 Match Rate**: **94%** (항목 42개 중 40개 일치)
- **Gap**: Critical 0, Major 2, Minor 4
- **Design Drift**: 5건 (설계 문서 ↔ 현실 괴리 — 문서 수정 대상)
- **Phase 2/4 Deferred**: 7 그룹 (설계대로 보류)

## 2. Phase 1 Match Rate (카테고리별)

| Category | Items | Match | Rate | Status |
|----------|:----:|:----:|:----:|:----:|
| API endpoints (5 Phase 1) | 5 | 4 | 80% | ⚠ |
| Domain models (FE↔BE) | 7 | 7 | 100% | ✅ |
| Technical indicators (ta4j) | 6 | 6 | 100% | ✅ |
| Cache strategy (Phase 1 keys/TTLs) | 5 | 5 | 100% | ✅ |
| External API clients | 4 | 4 | 100% | ✅ |
| FE components (Phase 1) | 5 | 5 | 100% | ✅ |
| Disclaimer / legal scaffold | 4 | 3 | 75% | ⚠ |
| Common infra (errors/CORS/Makefile) | 6 | 6 | 100% | ✅ |
| **Total** | **42** | **40** | **94%** | ✅ |

## 3. Gap 목록

### Critical
없음.

### Major

#### M-1 Search endpoint 경로 불일치
- **설계**: `GET /api/v1/search?q=` (§4.1 line 319)
- **구현**: `GET /api/v1/stocks/search?q=` (`StockController.java:63`, `stocks.ts:15`)
- **영향**: FE/BE 내부 일관되어 동작엔 문제 없으나 설계 계약과 불일치.
- **해결안**:
  - (A) 경로를 최상위 `/api/v1/search` 로 이동 (BE + FE 한 줄 변경)
  - (B) 설계 §4.1 을 현재 경로로 수정 (권장 — ticker 스코프 그룹과 일관성 좋음)

#### M-2 `/detail` 엔드포인트 Phase 조기 구현
- **설계**: Phase 2 로 지정 (§4.1 line 326)
- **구현**: Phase 1 에서 이미 live (news=null, aiSignal=null 반환)
- **FE 사용 여부**: 미사용 (`stock-detail-view.tsx` 는 개별 블록 훅 5개 호출)
- **해결안**: 설계에 "Phase 1 scaffold, content hydrated in Phase 2" 로 주석 추가.
  또는 FE 에서 `/detail` 로 전환해 호출 횟수 축소 (Phase 2 착수 시 재검토).

### Minor

#### m-1 `Quote.volume` 항상 0
- **원인**: Finnhub free `/quote` 응답에 volume 없음 (`FinnhubClient.java:101`)
- **설계 §3.2**: `volume: number` 필수 필드
- **해결안**:
  - Twelve Data `/time_series` 최신 bar 에서 hydrate (권장)
  - 또는 설계에 "volume은 candles 최신값 기준" 주석

#### m-2 `SearchHit.exchange` 타입 거짓말
- **BE**: Finnhub free `/search` 응답에 exchange 없어 `null` (`SearchService.java:39`)
- **FE 타입**: `exchange: string` (required) — 런타임은 null 허용이나 TS 계약 위반
- **해결안**: `apps/web/src/types/stock.ts:11` 을 `string | null` 로 변경

#### m-3 Indicator tooltipsKo 키 `ma` vs field `movingAverage`
- 지표 필드명은 `movingAverage` 로 통일했으나 tooltipsKo 맵 키는 `ma` 유지
- **평가**: i18n 딕셔너리 키라 짧은 키가 자연스러움. 의도적 유지. 감점 없음.

#### m-4 `TimeFrame` enum 식별자 명명
- 설계는 `1D..5Y` 문자열. Java 식별자가 숫자로 시작 불가라 `D1..Y5` 로 구현 후 `fromCode()`/`code()` 로 브릿지.
- **평가**: 계약 보존. 설계 §10 주석 추가 권장.

## 4. Design Drift (문서 수정 필요)

| # | Drift | 설계 위치 | 현실 | Action |
|---|-------|----------|------|--------|
| D-1 | OHLCV provider = Finnhub `/stock/candle` | §4.3 line 488 | Twelve Data `/time_series` (Finnhub free 403) | §4.3 hybrid 전략 반영, `planning/04-data-sources.md` 링크 |
| D-2 | `/detail` Phase 2 | §4.1 line 326 | Phase 1 scaffold | "Phase 1 scaffold, content is Phase 2" 주석 |
| D-3 | `indicators.ma` 필드명 | §3.2 line 191, §4.2 line 375 | `movingAverage` (FE 규약 맞춤) | 스니펫 업데이트 |
| D-4 | Search path `/api/v1/search` | §4.1 line 319 | `/api/v1/stocks/search` | M-1 결정에 따라 동기화 |
| D-5 | Indicator tooltip 문구 | §4.2 line 377-381 | `IndicatorTooltips.java` 미세 차이 | 문구 동기화 |

## 5. Phase 2 / 4 Deferred (감점 제외)

**Phase 2 범위**
- `/news`, `/ai-signal` 엔드포인트 + `NewsService`, `AiSignalService`
- RAG 파이프라인 구성요소: `ContextAssembler`, `PromptBuilder`, `ResponseValidator`, `GeminiClient`
- `NewsPanel`, `AiSignalPanel`, 프롬프트 리소스, `forbidden-terms.json`, `LegalGuardFilter`
- `ai_signal_history` INSERT (V1 마이그레이션으로 테이블은 존재, writer 없음)
- Redis 키: `news:*`, `ai:*:v1`, `market:snapshot`, `news:market`, `popular:tickers`
- `/market/snapshot`, `/popular-tickers`, `/legal/disclaimer` 동적 엔드포인트
- FE: PopularTickers / MarketDashboard / Glossary 페이지
- `/detail` 응답의 news/aiSignal 실제 채우기

**Phase 4 범위**
- `/api/v1/auth/verify`, `/bookmarks`, 알림 스케줄러
- Supabase Auth JWT 검증 Resource Server 설정

**운영 환경 이전** (의도적 지연)
- Supabase Postgres / Upstash Redis → 현재 local docker-compose. Phase 1 범위 내 수용 가능.

## 6. 후속 조치 권고

1. **M-1 결정** (최소 변경, 최고 가성비): `/stocks/search` 유지 + 설계 §4.1 수정.
2. **Design sync commit** — D-1..D-5 배치로 `mvp.design.md` 1회 업데이트. 다음 Gap scan 노이즈 감소.
3. **FE 타입 보정** (m-2): `SearchHit.exchange: string | null`.
4. **m-1 volume** — Phase 2 시작 전 결정 (Twelve Data 최신 bar hydrate vs 설계에 0 명시).
5. **`/detail` 통합 테스트** — Phase 1 에서 가장 복잡한 surface 인데 명시적 테스트 없음. `@SpringBootTest` + WireMock 으로 블록 부분 실패 시나리오 커버 권장.

## 관련 구현 파일

### Backend
- `apps/api/src/main/java/com/aistockadvisor/stock/web/StockController.java`
- `apps/api/src/main/java/com/aistockadvisor/stock/service/StockDetailService.java`
- `apps/api/src/main/java/com/aistockadvisor/stock/service/IndicatorService.java`
- `apps/api/src/main/java/com/aistockadvisor/stock/service/CandleService.java`
- `apps/api/src/main/java/com/aistockadvisor/stock/infra/client/FinnhubClient.java`
- `apps/api/src/main/java/com/aistockadvisor/stock/infra/client/TwelveDataClient.java`
- `apps/api/src/main/java/com/aistockadvisor/cache/RedisCacheAdapter.java`
- `apps/api/src/main/resources/application.yml`
- `apps/api/src/main/resources/db/migration/V1__init.sql`

### Frontend
- `apps/web/src/types/stock.ts`
- `apps/web/src/lib/api/stocks.ts`
- `apps/web/src/features/stock-detail/stock-detail-view.tsx`
- `apps/web/src/features/stock-detail/indicators/indicators-panel.tsx`
- `apps/web/src/app/layout.tsx`
