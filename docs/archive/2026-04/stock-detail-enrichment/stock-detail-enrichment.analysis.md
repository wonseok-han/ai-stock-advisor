# stock-detail-enrichment Gap Analysis

> **Date**: 2026-04-23
> **Analyst**: gap-detector
> **Design**: `docs/02-design/features/stock-detail-enrichment.design.md`

## 1. Overall Scores

| Category | Score | Status |
|----------|:-----:|:------:|
| Design Match | 88% | [WARN] |
| Architecture Compliance | 98% | [OK] |
| Convention Compliance | 97% | [OK] |
| **Overall** | **92%** | [OK] |

## 2. Match Rate Summary

```
[OK]      Match:     20 items (57%)
[CHANGED] Changed:   10 items (29%)
[ADDED]   Added:      5 items (14%)
[MISSING] Missing:    0 items (0%)
```

## 3. Key Changes (Design != Implementation)

| # | Item | Design | Implementation | Impact |
|---|------|--------|----------------|--------|
| 1 | Data Source Strategy | FMP 단일 소스 | Yahoo quoteSummary(primary) + FMP(fallback) | High |
| 2 | FMP endpoint path | `/api/v3/profile/{symbol}` | `/profile?symbol={ticker}` (stable API) | Low |
| 3 | FmpProfile.ceo | 필드 존재 | 필드 제거 (미사용) | Low |
| 4 | FmpProfile field mapping | `mktCap`, `lastDiv` 직접 | `@JsonProperty` 매핑 | Low |
| 5 | StockDetailResponse overview 위치 | aiSignal 뒤 | quote 뒤 | Low |
| 6 | StockController.overview() null | BusinessException | null 반환 | Medium |
| 7 | formatMarketCap 소수점 | `.toFixed(1)` | `.toFixed(2)` | Low |
| 8 | formatRatio suffix | `suffix` 파라미터 | 파라미터 없음 | Low |
| 9 | CompanyOverviewPanel 경로 | `components/` 하위 | `features/stock-detail/` 직속 | Low |
| 10 | useCompanyOverview retry | `false` | `1` | Low |

## 4. Added Features (Design X, Implementation O)

| # | Item | Description |
|---|------|-------------|
| 1 | Yahoo quoteSummary | crumb/cookie 인증 + v10 quoteSummary API |
| 2 | FmpRatiosTtm | `/ratios-ttm` 호출로 P/E, EPS 보강 |
| 3 | InfoTooltip 컴포넌트 | `@floating-ui/react` 기반 공유 툴팁 |
| 4 | 산업/직원수 표시 | 6셀 -> 8셀로 정보 확대 |
| 5 | website/ipoDate 링크 | 기업 웹사이트 + IPO 날짜 표시 |

## 5. Missing Features

없음 - 설계의 모든 기능이 구현됨.

## 6. Conclusion

**Overall 92%** - 설계와 구현이 높은 수준으로 일치합니다. 모든 변경사항은 구현 과정에서의 합리적인 판단(Yahoo primary 전환, InfoTooltip 재사용 추출, FMP stable API 호환)에 기인하며, 누락된 기능은 없습니다.
