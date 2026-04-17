# Phase 4.5 Improvements Planning Document

> **Summary**: Phase 1~4 잔여 gap 해소 + 캔들 데이터 레이어 + 차트/마이페이지/알림 UX 개선
>
> **Project**: AI Stock Advisor
> **Author**: Claude + wonseok-han
> **Date**: 2026-04-17
> **Status**: Draft

---

## Executive Summary

| Perspective | Content |
|-------------|---------|
| **Problem** | 무료 API 제한으로 캔들 데이터가 부족하고, 마이페이지가 빈약하며, 알림 진입점이 없고, Phase 1~4 잔여 gap(exchange nullable, volume 소스, 환율 변동 등)이 미해소 상태 |
| **Solution** | Yahoo Finance 벌크 + on-demand + 일간 배치로 DB 기반 캔들 레이어 구축, 마이페이지 리디자인, 종목 상세 내 알림 설정 진입점, 기술지표 검증, 잔여 gap 일괄 해소 |
| **Function/UX Effect** | 1Y/5Y 캔들이 풍부하게 표시되고, 마이페이지에서 북마크·알림·계정을 한눈에 관리하며, 종목 상세에서 바로 알림 설정 가능 |
| **Core Value** | 데이터 신뢰성과 UX 완성도를 Phase 5(향후 과제) 진입 전에 확보하여 서비스 품질 기반 마련 |

---

## 1. Overview

### 1.1 Purpose

Phase 1~4까지 구현된 기능들의 품질을 높이고, 무료 API 제한으로 인한 데이터 부족 문제를 해소합니다.
특히 캔들 차트의 데이터 빈약함, 마이페이지 디자인 미흡, 알림 진입점 부재, 기술지표 정합성 미검증 등
사용자 체감 품질에 직결되는 이슈들을 일괄 개선합니다.

### 1.2 Background

- **캔들 데이터 부족**: Twelve Data 무료 플랜(8 req/min, 800 req/day)으로 실시간 조회만 가능하여 1Y/5Y 차트가 빈약
- **마이페이지 빈약**: 북마크 목록 + 알림 설정 + 로그아웃 버튼만 존재, 디자인이 구림
- **알림 진입점 부재**: 종목 상세 → 마이페이지 → 알림 설정이라는 간접 경로만 존재
- **잔여 gap**: Phase 1~4 아카이브에 기록된 Known gaps 미해소 (SearchHit.exchange, Quote.volume, MarketMover.volume, usdKrwChange 등)
- **기술지표 미검증**: ta4j 계산 결과가 실제 TradingView 수치와 일치하는지 확인 안 됨

### 1.3 Related Documents

- 기획 고정본: `docs/planning/02-features.md`, `docs/planning/06-roadmap.md`
- Phase 1 MVP: `docs/archive/2026-04/mvp/`
- Phase 2 RAG: `docs/archive/2026-04/phase2-rag-pipeline/`
- Phase 3 시장 대시보드: `docs/archive/2026-04/market-dashboard/`
- Phase 4 인증/북마크/알림: `docs/archive/2026-04/auth/`

---

## 2. Scope

### 2.1 In Scope

#### Step 1 — 캔들 데이터 레이어 (Data Layer)

- [ ] `candles` DB 테이블 (Flyway V8) — ticker, date, open, high, low, close, adj_close, volume
- [ ] `YahooFinanceClient` — yfinance Python 스크립트 또는 Java HTTP로 벌크 OHLCV 다운로드
- [ ] 초기 벌크 로드 스크립트 — 인기 종목 30개 × 5년 일봉 적재
- [ ] `CandleService` 리팩터 — DB 우선 조회 → API fallback → 비동기 DB persist
- [ ] 일간 배치 스케줄러 — 매일 장 마감 후 당일 캔들 DB 적재
- [ ] Adjusted close 정합 — Yahoo default=adjusted, Twelve Data `adjust=true`

#### Step 2 — 차트 개선 (Chart UX)

- [ ] 1D 타임프레임: 5분봉 → 장중 캔들스틱으로 정상 표시
- [ ] 1W 타임프레임: 30분봉 → 일봉 5개로 변경 (직관적)
- [ ] 1M/3M/1Y/5Y: DB 일봉/주봉으로 풍부한 캔들 표시
- [ ] 거래량 바 하단 서브차트 정렬 개선
- [ ] MA 오버레이 정상 동작 확인

#### Step 3 — 마이페이지 리디자인 (My Page UX)

- [ ] 프로필 섹션: 아바타(이니셜) + 이메일 + 가입일
- [ ] 북마크 섹션: 그리드 카드 레이아웃 (가격/변동률/미니차트 스파크라인)
- [ ] 알림 섹션: 종목별 알림 설정 카드 + 글로벌 푸시 on/off 토글
- [ ] 계정 섹션: 로그아웃 + 계정 삭제 (soft)
- [ ] 빈 상태 UI 개선 (일러스트 or 아이콘 + CTA)

#### Step 4 — 알림 진입점 (Notification Entry)

- [ ] 종목 상세 페이지에 알림 설정 버튼 (북마크 옆)
- [ ] 알림 설정 모달/패널: 가격 변동 임계치, 뉴스 알림, 시그널 변화 토글
- [ ] 북마크하지 않은 종목에 알림 설정 시 자동 북마크 추가

#### Step 5 — 잔여 Gap 해소 (Residual Gaps)

- [ ] `SearchHit.exchange` nullable 처리 (Phase 1 known gap)
- [ ] `Quote.volume` 소스 결정 — Twelve Data quote volume 필드 활용
- [ ] `MarketMover.volume` 필드 추가 (Phase 3 known gap)
- [ ] `usdKrwChange` 환율 변동 계산 (Phase 3 known gap)
- [ ] Rate Limiter (Bucket4j) — Phase 1 로드맵 미구현 항목
- [ ] Auth design 문서 동기화 — 실제 구현(two-chain, ES256+RS256)과 설계 문서 차이

#### Step 6 — 기술지표 검증 (Indicator QA)

- [ ] RSI(14) ta4j 결과 vs TradingView 비교 (AAPL, TSLA, MSFT)
- [ ] MACD(12,26,9) 비교
- [ ] Bollinger Band(20,2) 비교
- [ ] 불일치 시 계산 로직 수정 또는 데이터 정합성 이슈 해소

### 2.2 Out of Scope

- 실시간 스트리밍 시세 (Phase 5+)
- 모바일 앱 / FCM (Phase 5+)
- 포트폴리오 시뮬레이션 (Phase 5+)
- 새로운 기술지표 추가 (이치모쿠, 피보나치 등)
- 알림 발송 로직 자체 (BE 15분 스케줄러는 Phase 4에서 구현 완료)

---

## 3. Requirements

### 3.1 Functional Requirements

| ID | Requirement | Priority | Status |
|----|-------------|----------|--------|
| FR-01 | `candles` 테이블 생성 (ticker, date, OHLCV, adj_close, PK=ticker+date) | High | Pending |
| FR-02 | Yahoo Finance 벌크 OHLCV 다운로드 (인기 30종목 × 5년 일봉) | High | Pending |
| FR-03 | CandleService DB-first 조회 → Twelve Data fallback → 비동기 DB persist | High | Pending |
| FR-04 | on-demand 캔들 로드: 미적재 종목 첫 조회 시 Yahoo/Twelve Data에서 가져와 DB 저장 | High | Pending |
| FR-05 | 일간 배치: 매일 장 마감 후 적재된 종목의 당일 캔들 DB append | Medium | Pending |
| FR-06 | Adjusted close 정합: Yahoo adj_close 사용, Twelve Data adjust=true | High | Pending |
| FR-07 | 1W 타임프레임을 일봉 5개로 변경 | Medium | Pending |
| FR-08 | 1M/3M/1Y/5Y DB 일봉/주봉 기반 캔들 표시 | High | Pending |
| FR-09 | 마이페이지 리디자인: 프로필/북마크(카드)/알림/계정 4섹션 | Medium | Pending |
| FR-10 | 종목 상세 알림 설정 버튼 + 모달 | Medium | Pending |
| FR-11 | 미북마크 종목 알림 설정 시 자동 북마크 | Low | Pending |
| FR-12 | SearchHit.exchange nullable 처리 | Low | Pending |
| FR-13 | Quote.volume Twelve Data 연동 | Medium | Pending |
| FR-14 | MarketMover.volume 필드 추가 | Low | Pending |
| FR-15 | usdKrwChange 환율 변동 계산 | Low | Pending |
| FR-16 | Bucket4j Rate Limiter 적용 | Medium | Pending |
| FR-17 | RSI/MACD/Bollinger Band ta4j 결과 검증 (3종목) | Medium | Pending |
| FR-18 | Auth design 문서 동기화 | Low | Pending |

### 3.2 Non-Functional Requirements

| Category | Criteria | Measurement Method |
|----------|----------|-------------------|
| Performance | DB 캔들 조회 P95 < 100ms (인덱스 활용) | `GET /stocks/{t}/candles` 응답 시간 |
| Storage | Supabase 500MB 내 유지 (인기 30종목 5Y ≈ 3MB, 1000종목 ≈ 120MB) | Supabase dashboard |
| Data Freshness | 일간 배치: 장 마감 후 1시간 내 적재 완료 | 스케줄러 로그 |
| UX | 마이페이지 FCP < 1.5s | Lighthouse |
| Reliability | On-demand fallback: DB miss 시 API 조회 + DB persist 3초 내 완료 | 로그 + 메트릭 |

---

## 4. Success Criteria

### 4.1 Definition of Done

- [ ] `candles` 테이블에 인기 30종목 × 5년 일봉 적재 완료
- [ ] 1Y/5Y 차트에 풍부한 캔들(260+개)이 표시됨
- [ ] 미적재 종목 첫 조회 시 on-demand로 데이터 로드 후 DB 저장
- [ ] 마이페이지 4섹션 리디자인 완료
- [ ] 종목 상세에서 알림 설정 가능
- [ ] 잔여 gap 6건 해소
- [ ] 기술지표 3종 × 3종목 검증 완료

### 4.2 Quality Criteria

- [ ] BE 빌드 성공 (`make api-build`)
- [ ] FE 빌드 성공 (`make web-build`)
- [ ] FE lint/typecheck 통과 (`make web-check`)
- [ ] Gap analysis ≥ 90%

---

## 5. Risks and Mitigation

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| Yahoo Finance API 차단/변경 | High | Medium | yfinance 라이브러리 대신 직접 HTTP, User-Agent 로테이션, CSV 수동 다운로드 백업 |
| Supabase 500MB 초과 | High | Low | 종목 수 제한(1000개), 5Y 이상 데이터 정리, 주봉만 저장 옵션 |
| Twelve Data adjust=true 미지원 | Medium | Low | Yahoo adj_close를 SoR로 사용, Twelve Data는 intraday 전용 |
| ta4j 지표 불일치 | Medium | Medium | 파라미터/데이터 범위 차이 원인 분석, 필요 시 자체 계산 로직 |
| 벌크 로드 시 Supabase rate limit | Medium | Medium | 배치 insert (1000 rows/batch), 인기 종목 우선 순차 적재 |

---

## 6. Architecture Considerations

### 6.1 Project Level Selection

| Level | Characteristics | Recommended For | Selected |
|-------|-----------------|-----------------|:--------:|
| **Starter** | Simple structure | Static sites | |
| **Dynamic** | Feature-based modules, BaaS integration | Web apps with backend | **V** |
| **Enterprise** | Strict layer separation, microservices | High-traffic systems | |

### 6.2 Key Architectural Decisions

| Decision | Options | Selected | Rationale |
|----------|---------|----------|-----------|
| 캔들 벌크 로드 | Python yfinance / Java HTTP Yahoo | Python yfinance 스크립트 | 성숙한 라이브러리, 벌크 데이터 접근 용이, 초기 로드 1회성 |
| 일간 배치 | Spring @Scheduled / 외부 cron | Spring @Scheduled | 기존 Push 스케줄러 패턴 재사용, 인프라 추가 없음 |
| DB 캔들 스키마 | 단일 테이블 / interval별 파티션 | 단일 테이블 (일봉 only) | 단순성, 인기 종목 규모에서 파티션 불필요 |
| On-demand fallback | 동기 로드 / 비동기 로드+stale 반환 | 동기 로드 (첫 조회 시 대기) | 빈 차트보다 3초 대기가 나은 UX |
| 주봉 생성 | DB 집계 쿼리 / 서비스 레벨 변환 | DB 일봉 → 서비스 레벨 weekly 집계 | 저장 공간 절약, 일봉이 SoR |
| Rate Limiter | Bucket4j / Resilience4j | Bucket4j | Phase 1 로드맵 원안, Spring Boot 통합 우수 |

### 6.3 데이터 흐름

```
[초기 벌크 로드]
  yfinance script → CSV/JSON → Flyway seed or Spring batch insert → candles 테이블

[실시간 조회]
  FE → GET /stocks/{t}/candles?tf=1Y
    → CandleService
      → DB 조회 (candles WHERE ticker=? AND date BETWEEN ?)
      → 데이터 있음 → 반환
      → 데이터 없음 → Yahoo/TwelveData API 호출
                     → 응답을 DB에 비동기 저장
                     → 즉시 반환

[일간 배치]
  @Scheduled(cron = "0 0 22 * * MON-FRI")  // UTC 22:00 = KST 07:00 (미장 마감 후)
    → 적재된 종목 목록 조회
    → Yahoo Finance daily quote 호출
    → candles 테이블 INSERT ON CONFLICT DO NOTHING
```

---

## 7. Convention Prerequisites

### 7.1 Existing Project Conventions

- [x] `CLAUDE.md` has coding conventions section
- [x] ESLint configuration
- [x] Prettier configuration
- [x] TypeScript configuration (`tsconfig.json`)

### 7.2 Conventions to Define/Verify

| Category | Current State | To Define | Priority |
|----------|---------------|-----------|:--------:|
| **Naming** | Exists (CLAUDE.md) | — | — |
| **Folder structure** | Exists | `candle/` 도메인 패키지 추가 | Medium |
| **Import order** | Exists | — | — |
| **Environment variables** | Exists | `YAHOO_FINANCE_*` 추가 여부 결정 | Medium |

### 7.3 Environment Variables Needed

| Variable | Purpose | Scope | To Be Created |
|----------|---------|-------|:-------------:|
| (없음) | Yahoo Finance는 API key 불필요 (yfinance) | — | — |
| `BUCKET4J_CAPACITY` | Rate limiter 버킷 용량 | Server | V |
| `BUCKET4J_REFILL_RATE` | Rate limiter 토큰 리필 속도 | Server | V |

---

## 8. Implementation Order (Suggested Steps)

> Design 문서에서 구체화하되, 대략적 구현 순서는 다음과 같습니다.

| Step | Scope | Estimated Files | Depends On |
|------|-------|-----------------|------------|
| **Step 1** | `candles` 테이블 + Entity + Repository | ~5 | — |
| **Step 2** | `YahooFinanceClient` + 벌크 로드 스크립트 | ~3 | Step 1 |
| **Step 3** | `CandleService` 리팩터 (DB-first + fallback) | ~3 | Step 1, 2 |
| **Step 4** | TimeFrame/차트 표현 개선 (BE + FE) | ~6 | Step 3 |
| **Step 5** | 일간 배치 스케줄러 | ~2 | Step 1, 3 |
| **Step 6** | 마이페이지 리디자인 (FE) | ~8 | — |
| **Step 7** | 종목 상세 알림 설정 (FE + BE) | ~5 | — |
| **Step 8** | 잔여 Gap 해소 (BE + FE) | ~10 | — |
| **Step 9** | 기술지표 검증 + 수정 | ~3 | Step 3 |
| **Step 10** | Bucket4j Rate Limiter | ~4 | — |

**총 예상**: ~50 파일, ~3,000+ lines

---

## 9. Next Steps

1. [ ] Write design document (`phase4.5-improvements.design.md`)
2. [ ] Review & approve plan
3. [ ] Start implementation (Step 1~10 순차 진행)

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 0.1 | 2026-04-17 | Initial draft | Claude + wonseok-han |
