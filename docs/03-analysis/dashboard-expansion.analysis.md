# Gap Analysis: dashboard-expansion

> 설계 문서 vs 구현 코드 비교 분석 (PDCA Check Phase)

| 항목 | 값 |
|------|-----|
| Feature | dashboard-expansion |
| Design | `docs/02-design/features/dashboard-expansion.design.md` |
| 분석일 | 2026-04-23 |
| Match Rate | **97.8%** |

---

## 1. 점수 요약

| Category | Score | Status |
|----------|:-----:|:------:|
| Design Match | 97.8% | PASS |
| Architecture Compliance | 100% | PASS |
| Convention Compliance | 100% | PASS |

---

## 2. 항목별 매칭 결과

| Category | Total | Matched | Enhanced | Changed | Missing |
|----------|:-----:|:-------:|:--------:|:-------:|:-------:|
| BE Domain | 3 | 3 | 0 | 0 | 0 |
| BE FmpClient | 3 | 3 | 0 | 0 | 0 |
| BE SectorPerformanceService | 8 | 8 | 0 | 0 | 0 |
| BE MarketOverviewService | 5 | 3 | 2 | 0 | 0 |
| BE MarketController | 3 | 3 | 0 | 0 | 0 |
| FE Types | 2 | 2 | 0 | 0 | 0 |
| FE API | 1 | 1 | 0 | 0 | 0 |
| FE Hook | 4 | 4 | 0 | 0 | 0 |
| FE MarketOverview | 8 | 5 | 3 | 0 | 0 |
| FE SectorPerformance | 5 | 4 | 0 | 1 | 0 |
| FE MarketDashboard | 3 | 3 | 0 | 0 | 0 |
| **Total** | **45** | **39** | **5** | **1** | **0** |

---

## 3. 변경 항목 (Design != Implementation)

| # | Item | Design | Implementation | Impact |
|---|------|--------|----------------|--------|
| 1 | 섹터 표시 UI | SectorChip (가로 스크롤 칩) | SectorBar (세로 바 차트) | Low — UX 개선 |

> SectorBar는 11개 섹터의 상대적 강약을 시각적으로 더 직관적으로 보여주며, 가로 스크롤 없이 한 화면에 표시. 사용자가 기존 칩 디자인이 저수준하다고 피드백하여 개선.

---

## 4. Enhancement (사용자 요청 의도적 개선)

| # | Item | Description |
|---|------|-------------|
| 1 | Russell 2000 | INDEX_SYMBOLS에 `^RUT` 추가 (소형주 지수) |
| 2 | VIX 매크로 이동 | INDEX → MACRO 섹션으로 재분류 |
| 3 | DXY | MACRO_SYMBOLS에 `DX-Y.NYB` 추가 (달러 인덱스) |
| 4 | Silver | MACRO_SYMBOLS에 `SI=F` 추가 |
| 5 | Copper | MACRO_SYMBOLS에 `HG=F` 추가 |
| 6 | 매크로 2분류 | "변동성·환율·금리" + "원자재" 2개 섹션 분리 |
| 7 | InfoTooltip | 전 섹션 + 전 카드에 한국어 설명 툴팁 |
| 8 | VIX highlight | VIX >= 20 amber ring, >= 30 red ring |
| 9 | DXY/VIX 단위 | 달러/퍼센트 접두사 없이 순수 숫자 표시 |

---

## 5. 누락 항목

없음 (0건)

---

## 6. 권장 사항

1. **설계 문서 업데이트**: 9개 enhancement 항목 + SectorBar 변경을 설계 문서에 반영하여 SoR 일관성 유지 (권장, 필수 아님)
2. **즉시 조치 불필요**: 모든 설계 항목 구현 완료, Missing 0건

---

## 7. 결론

**Match Rate 97.8%** — `/pdca report dashboard-expansion` 진행 가능.
