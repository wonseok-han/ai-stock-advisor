# AI Stock Advisor

AI 기반 미국 주식 투자 보조 웹서비스. **초보 투자자**를 위한 참고용 분석 도구.

> ⚠️ 본 서비스는 투자 자문이 아닌 **정보 제공 및 참고용 분석 도구**입니다. 모든 투자 판단과 책임은 사용자 본인에게 있습니다.

---

## 핵심 기능 (목표)

1. 티커/종목명 입력 → AI가 차트/뉴스/시장 지표를 종합 분석해 **시그널** 제공 (매수/중립/매도 참고용)
2. TradingView 스타일 차트 (캔들 + 지표 오버레이)
3. 종목별 최신 뉴스 (LLM 기반 한국어 번역/요약)
4. 실시간(15분 지연) 시세 + MACD / 볼린저밴드 / RSI 등 기술적 지표
5. 시장 대시보드: 주요 지수, VIX, USD/KRW, 시장 뉴스
6. 북마크 + 푸시 알림

## 기술 스택 (요약)

- **Frontend:** Next.js 16 (App Router, TypeScript) + React 19 + Tailwind 4
- **Backend:** Spring Boot 3.x (Java 21)
- **DB:** PostgreSQL (Supabase)
- **Cache:** Redis (Upstash)
- **AI:** Gemini 1.5 Flash (RAG 방식)
- **Chart:** TradingView Lightweight Charts
- **Auth:** Supabase Auth + Spring Boot JWT 검증
- **Deploy:** Vercel (FE) + Fly.io / Oracle Cloud Free Tier (BE)

## 문서 구조

기획 문서는 `docs/planning/` 아래에 있습니다.

| # | 문서 | 내용 |
|---|---|---|
| 01 | [overview.md](docs/planning/01-overview.md) | 서비스 개요 & 포지셔닝 |
| 02 | [features.md](docs/planning/02-features.md) | 기능 명세 |
| 03 | [architecture.md](docs/planning/03-architecture.md) | 시스템 아키텍처 & 기술 스택 |
| 04 | [data-sources.md](docs/planning/04-data-sources.md) | 외부 API 및 데이터 소스 |
| 05 | [ai-strategy.md](docs/planning/05-ai-strategy.md) | AI 적용 전략 |
| 06 | [roadmap.md](docs/planning/06-roadmap.md) | 단계별 로드맵 |
| 07 | [legal-compliance.md](docs/planning/07-legal-compliance.md) | 법적 고려사항 |

## 현재 상태

**Phase 0: 기획 진행 중** — 아직 코드 없음. 기획 문서 작업 → MVP 스펙 확정 → 개발 시작 예정.
