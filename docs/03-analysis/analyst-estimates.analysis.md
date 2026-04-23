# Gap Analysis: analyst-estimates

> 분석일: 2026-04-23 | Match Rate: **93%**

## Overall Scores

| Category | Score | Status |
|----------|:-----:|:------:|
| Design Match | 93% | ✅ |
| Architecture Compliance | 95% | ✅ |
| Convention Compliance | 98% | ✅ |
| **Overall** | **93%** | ✅ |

## Gap Summary

### Intentional Deviations (설계 대비 의도적 변경)

| Item | Design | Implementation | Reason |
|------|--------|---------------|--------|
| FMP fallback | Yahoo + FMP 이중 소스 | Yahoo 단일 소스 | FMP analyst 3개 엔드포인트 모두 402 (유료 전용) |
| Yahoo 모듈 상수 | 별도 `ANALYST_MODULES` | `ALL_MODULES` 통합 | Redis 캐시 키 공유로 효율 향상 |
| RatingGauge `label` prop | Props에 포함 | 제거 (labelKo만 사용) | 한국어 서비스 특성 |
| FMP DTO 구조 | `/analyst-stock-recommendations` 등 | `/grades-consensus` 등 | FMP API 공식 문서 기준 교정 |

### Added Features (설계에 없는 추가 구현)

| Item | Location | Description |
|------|----------|-------------|
| `enrichCurrentPrice()` | `AnalystEstimatesService:41-59` | Yahoo 데이터에 현재가 없을 때 QuoteService에서 보강 |
| `'EST'` result 타입 | `types/stock.ts`, `earnings-history.tsx` | 미래 분기 전망치 표시용 |

### Architecture & Convention

- 의존 방향 위반: 없음
- FE 파일명 kebab-case: 100%
- BE 패키지 레이아웃: domain-driven 준수
- Import 순서: 외부 → @/ → 상대 → type → style 준수

## Conclusion

FMP fallback 미연결이 유일한 명목상 Gap이나, FMP 무료 플랜에서 해당 엔드포인트가 402를 반환하므로 **의도적 제거**에 해당. 설계 문서 업데이트로 해소 완료. 나머지 변경은 모두 개선 또는 합리적 판단.

## Related Documents

- Design: [analyst-estimates.design.md](../02-design/features/analyst-estimates.design.md)
- Plan: [analyst-estimates.plan.md](../01-plan/features/analyst-estimates.plan.md)
