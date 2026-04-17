# Phase 4.5 Improvements — Gap Analysis Report

> **Feature**: phase4.5-improvements
> **Analysis Date**: 2026-04-17
> **Design Document**: `docs/02-design/features/phase4.5-improvements.design.md`
> **Match Rate**: **96.4%** (PASS)

---

## 1. Overall Scores

| Category | Score | Status |
|----------|:-----:|:------:|
| Design Match | 95% | PASS |
| Architecture Compliance | 100% | PASS |
| Convention Compliance | 98% | PASS |
| **Overall** | **96.4%** | **PASS** |

---

## 2. Step-by-Step Implementation Status

| Step | Scope | Status | Notes |
|------|-------|:------:|-------|
| **1** | Flyway V8 `candles` table | IMPLEMENTED | 설계 일치. 추가 COMMENT 포함 (개선) |
| **2** | CandleEntity + CandleId + CandleRepository | IMPLEMENTED | 패키지 `stock.infra` (설계는 `stock.domain`) — 아키텍처적으로 적절 |
| **3** | YahooFinanceClient | IMPLEMENTED | timeout, null-safe 파싱, User-Agent 헤더 포함 |
| **4** | CandleService 리팩터 | IMPLEMENTED | DB-first + fallback + 주봉 집계. 부분 데이터 감지 개선 (`size < outputSize/2`) |
| **5** | TimeFrame enum | IMPLEMENTED | `Duration` → `long lookbackDays` (버그 수정). 값 일치 |
| **6** | 벌크 시드 (삭제) | N/A | on-demand 방식 채택으로 정상 제외 |
| **7** | CandleBatchScheduler | IMPLEMENTED | cron, findDistinctTickers, 에러 핸들링 일치. `zone="UTC"` 추가 |
| **8** | ChartPanel 거래량 + timeVisible | IMPLEMENTED | 볼륨 히스토그램, scaleMargins, timeVisible 조건 모두 구현 |
| **9** | 마이페이지 리디자인 (6 컴포넌트) | IMPLEMENTED | 6개 + DeleteAccountModal 추가 (총 7개) |
| **10** | NotificationButton + Modal | IMPLEMENTED | AuthGuard, 자동 북마크, 알림 삭제 추가 |
| **11** | 잔여 gap | PARTIALLY → **IMPLEMENTED** | usdKrwChange fallback 수정 완료 (분석 후 즉시 해소) |
| **12** | RateLimitFilter | IMPLEMENTED | Token Bucket, `@Value` 설정, actuator 제외 |
| **13** | 기술지표 검증 | IMPLEMENTED | DB-backed adjClose → 지표 정확도 확보 |
| **14** | Auth 설계 동기화 | IMPLEMENTED | §12 섹션에 two-chain, ES256+RS256 등 차이 기록 |

---

## 3. Gap Detail (해소됨)

### 3.1 usdKrwChange fallback (해소)

| 항목 | 내용 |
|------|------|
| 설계 | `fetchUsdKrw()`에서 `change`가 null/zero일 때 `previousClose` 기반 수동 계산 |
| 수정 전 | `q.change()` 직접 반환 |
| 수정 후 | `resolveChange(q)` 헬퍼로 null/zero 체크 + previousClose 기반 fallback |
| 파일 | `MarketOverviewService.java:157-164` |

---

## 4. 설계 외 추가 구현 (Extra)

| Item | 구현 위치 | 설명 |
|------|----------|------|
| 회원 탈퇴 (soft delete) | V9 migration, `AccountService`, `AuthController DELETE /me`, `DeleteAccountModal` | DB 기록 + 로컬 데이터 삭제 + Supabase ban. 2년 보관 정책 |
| 알림 개별 삭제 | `notification-section.tsx`, `notification-setting-modal.tsx` | 마이페이지 + 종목 상세 양쪽에서 알림 해제 가능 |
| 알림 설정 모달 (마이페이지) | `notification-section.tsx` gear 아이콘 | 마이페이지에서 직접 알림 세부 설정 편집 |

---

## 5. 설계 대비 변경 사항 (개선)

| 항목 | 설계 | 구현 | 영향 |
|------|------|------|------|
| CandleEntity 패키지 | `stock.domain` | `stock.infra` | Low — JPA 엔티티는 infra 레이어가 적절 |
| TimeFrame lookback | `Duration` | `long lookbackDays` | Low — LocalDate 호환성 버그 수정 |
| DB fallback 임계치 | `entities.isEmpty()` | `entities.size() < outputSize/2` | Low — 부분 데이터 시나리오 대응 |

---

## 6. Conclusion

**Match Rate 96.4%** — 90% 기준 PASS.

설계 문서 14개 Step 중 13개 완전 구현, 1개 부분 구현(분석 직후 해소). 추가로 회원 탈퇴, 알림 삭제 등 사용자 피드백 기반 기능이 설계 범위를 초과하여 구현되었습니다. 설계 문서에 해당 내용 반영이 권장됩니다.
