# Phase 3: Market Dashboard Plan

## Executive Summary

| 관점 | 설명 |
|---|---|
| **Problem** | 현재 메인(`/`)이 검색 박스만 있어 "오늘 시장이 어때?"라는 가장 기본적인 질문에 답하지 못함 |
| **Solution** | 주요 지수·VIX·환율·시장 뉴스·급등락 종목을 한 화면에 모아 시장 전체 스냅샷 제공 |
| **Function UX Effect** | 앱 진입 즉시 시장 분위기 파악 → 관심 종목 클릭 → 종목 상세 연결, 체류 시간 증가 |
| **Core Value** | "첫 화면이 오늘 시장 분위기에 대한 답이 된다" — 로드맵 Phase 3 완료 조건 직접 달성 |

---

## 1. 배경 및 목표

### 1.1 배경
- Phase 1 (MVP 코어) + Phase 2 (AI 레이어) 완료, 배포 완료 (Render + Vercel + Supabase + Upstash)
- 현재 메인 페이지는 검색 박스 + 면책 문구만 존재 → 사용자가 티커를 알아야만 진입 가능
- 로드맵 Phase 3 목표: "메인에서 오늘 시장 분위기가 한눈에 보인다"

### 1.2 목표
- 메인 페이지를 시장 대시보드로 전환
- BE 3개 신규 엔드포인트 (`/market/overview`, `/market/news`, `/market/movers`)
- FE 대시보드 위젯 구성 (지수 카드, VIX 게이지, 환율, 시장 뉴스, 급등락 종목)
- 기존 검색 기능 유지 (대시보드 상단 통합)

### 1.3 성공 기준
- [ ] 메인 페이지에 지수/VIX/환율이 표시됨
- [ ] 시장 뉴스 상위 10건이 한국어 요약과 함께 표시됨
- [ ] 급등/급락 종목 각 5개가 표시되고, 클릭하면 종목 상세로 이동
- [ ] 모든 데이터에 적절한 캐시 전략 적용 (외부 API rate limit 준수)
- [ ] 면책 고지 유지

---

## 2. 스코프

### 2.1 In-Scope

#### BE (Spring Boot)

| # | 엔드포인트 | 설명 | 데이터 소스 | 캐시 TTL |
|---|---|---|---|---|
| BE-1 | `GET /api/v1/market/overview` | 주요 지수, VIX, USD/KRW | Finnhub quote (지수 심볼) | 5분 |
| BE-2 | `GET /api/v1/market/news` | 시장 뉴스 상위 10건 (한국어 번역) | Finnhub market news + Gemini 번역 | 15분 |
| BE-3 | `GET /api/v1/market/movers` | 급등/급락 종목 각 5개 | Finnhub (유료 제약 시 대안) | 15분 |

#### FE (Next.js)

| # | 컴포넌트 | 설명 |
|---|---|---|
| FE-1 | `MarketOverview` | 지수 카드 (S&P500, Nasdaq, Dow) + VIX + USD/KRW |
| FE-2 | `MarketNews` | 시장 뉴스 피드 (제목·요약·출처·시각) |
| FE-3 | `MarketMovers` | 급등/급락 탭 또는 2-column, 종목 클릭 → `/stock/[ticker]` |
| FE-4 | 메인 페이지 리팩터링 | 기존 검색 박스 + 대시보드 위젯 통합 레이아웃 |

### 2.2 Out-of-Scope
- Russell 2000 지수 (Finnhub 무료 미지원 시 제외)
- 10Y 국채 금리 (Finnhub 무료 미지원, Yahoo 비공식 → Phase 5+)
- 실시간 스트리밍 (WebSocket)
- 섹터별 히트맵
- 시장 뉴스 AI 감성 분석

---

## 3. 데이터 소스 분석

### 3.1 Finnhub 무료 API 활용

| 데이터 | Finnhub API | 무료 지원 | 비고 |
|---|---|---|---|
| 지수 시세 | `GET /quote?symbol=^GSPC` | O (심볼 형태 확인 필요) | S&P500, Nasdaq, Dow |
| VIX | `GET /quote?symbol=^VIX` | 확인 필요 | 미지원 시 대안 |
| USD/KRW 환율 | `GET /forex/rates?base=USD` | O | |
| 시장 뉴스 | `GET /news?category=general` | O (30 req/min) | |
| 급등/급락 | 직접 API 없음 | X | 별도 구현 필요 |

### 3.2 지수 심볼 매핑 (확인 필요)

Finnhub 에서 지수 quote 를 가져오려면 심볼 형태가 다를 수 있음:
- Yahoo 형식: `^GSPC`, `^IXIC`, `^DJI`
- Finnhub 형식: 다를 수 있음 → **구현 시 테스트 필수**
- 대안: Twelve Data 또는 Alpha Vantage 지수 조회

### 3.3 급등/급락 종목 구현 전략

Finnhub 무료에 movers API 가 없으므로 대안:
1. **인기 종목 목록 하드코딩 (20~30개) + quote 일괄 조회 → 변동률 정렬** — 가장 현실적
2. Twelve Data movers API (유료)
3. 크롤링 (비추)

> **추천: 옵션 1** — 인기 종목 풀을 DB 또는 상수로 관리, 15분 캐시

---

## 4. 기술 설계 방향

### 4.1 BE 패키지 구조

```
com.aistockadvisor.market/
  ├── web/
  │   └── MarketController.java        # 3개 엔드포인트
  ├── service/
  │   ├── MarketOverviewService.java    # 지수/VIX/환율 조합
  │   ├── MarketNewsService.java        # 뉴스 + 번역
  │   └── MarketMoversService.java      # 인기 종목 quote → 정렬
  ├── domain/
  │   ├── MarketIndex.java              # 지수 DTO
  │   ├── MarketOverview.java           # 조합 응답
  │   └── MarketMover.java              # 급등/급락 종목
  └── infra/
      └── (기존 FinnhubClient 재사용)
```

### 4.2 FE 폴더 구조

```
features/market-dashboard/
  ├── market-overview.tsx
  ├── market-news.tsx
  ├── market-movers.tsx
  └── hooks/
      ├── use-market-overview.ts
      ├── use-market-news.ts
      └── use-market-movers.ts
```

### 4.3 캐시 전략

| 데이터 | Redis Key 패턴 | TTL | 이유 |
|---|---|---|---|
| 지수/VIX/환율 | `market:overview` | 5분 | 준실시간, Finnhub 60 req/min 보호 |
| 시장 뉴스 | `market:news` | 15분 | 번역 비용 절감 |
| 급등/급락 | `market:movers` | 15분 | quote 대량 호출 방지 |

### 4.4 외부 API 호출 예산

| 데이터 | 호출 수/갱신 | 15분 기준 | Finnhub 분당 한도 |
|---|---|---|---|
| 지수 3개 + VIX + 환율 | 5 calls/5min | ~15 calls/15min | |
| 뉴스 | 1 call/15min | 1 call/15min | |
| Movers (20종목 quote) | 20 calls/15min | 20 calls/15min | |
| **합계** | | **~36 calls/15min** | 60/min = 900/15min, 충분 |

---

## 5. 구현 순서

| Step | 범위 | 설명 | 예상 파일 |
|---|---|---|---|
| 1 | BE: 도메인 + 서비스 골격 | `market` 패키지, DTO, 서비스 인터페이스 | 5~8 파일 |
| 2 | BE: `/market/overview` | 지수/VIX/환율 조합, Redis 캐시 | 3~4 파일 |
| 3 | BE: `/market/news` | Finnhub market-news + 기존 NewsTranslator 재사용 | 2~3 파일 |
| 4 | BE: `/market/movers` | 인기 종목 풀 + quote 일괄 → 변동률 정렬 | 3~4 파일 |
| 5 | FE: 대시보드 위젯 | MarketOverview + MarketNews + MarketMovers | 6~8 파일 |
| 6 | FE: 메인 페이지 통합 | 기존 검색 박스 + 대시보드 레이아웃 | 1~2 파일 |
| 7 | 통합 테스트 + 배포 검증 | BE 단위 테스트, FE 동작, Render/Vercel 배포 | 2~3 파일 |

---

## 6. 리스크 및 대응

| 리스크 | 영향 | 대응 |
|---|---|---|
| Finnhub 지수 quote 미지원 | 지수 데이터 없음 | Twelve Data 또는 Alpha Vantage fallback |
| VIX 심볼 미확인 | VIX 게이지 비어있음 | 대안 소스 또는 Phase 3.1 로 분리 |
| 환율 API 정확도 | USD/KRW 지연 | exchangerate.host 대안 |
| Movers 하드코딩 풀 편향 | 실제 급등 종목 누락 | 풀 크기 확장 (50종목) 또는 Phase 5 개선 |
| Render cold start + 대시보드 | 첫 로딩 2분+ | FE 로딩 스켈레톤 + "데이터 준비 중" 안내 |
| Upstash 10k cmd/day 한도 | 대시보드 캐시 miss 시 소진 | TTL 충분히 길게, 불필요 조회 방지 |

---

## 7. 의존성

| 항목 | 상태 | 비고 |
|---|---|---|
| FinnhubClient | 구현 완료 | quote/search/profile/news 지원, 지수 심볼 추가 테스트 필요 |
| NewsTranslator | 구현 완료 | 시장 뉴스에도 재사용 가능 (ticker 파라미터 옵셔널화 필요 여부 확인) |
| Redis 캐시 인프라 | 구현 완료 | 기존 패턴 (`RedisTemplate`) 재사용 |
| Render/Vercel 배포 | 구성 완료 | 환경변수 추가 불필요 (기존 API 키 재사용) |

---

## 8. 비기능 요구사항

| 항목 | 목표 |
|---|---|
| 대시보드 로딩 (캐시 hit) | < 500ms |
| 대시보드 로딩 (캐시 miss) | < 3s (외부 API 호출 포함) |
| 모바일 반응형 | 카드 1열 스택, 가로 스크롤 없음 |
| 면책 고지 | 대시보드 하단 유지 |
| 에러 처리 | 부분 실패 허용 (지수 실패해도 뉴스는 표시) |
