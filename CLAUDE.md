# 지금이니?! (Nowini)

> 초보 투자자를 위한 AI 기반 미국 주식 참고/분석 웹서비스. 투자 자문이 아닌 **정보 제공 및 참고용 분석 도구**.

---

## Core Principles

### 1. Automation First, Commands are Shortcuts

PDCA 방법론은 자동 적용됩니다. `/pdca plan|design|do|analyze|report` 는 파워유저용 단축키입니다.

### 2. SoR (Single Source of Truth) 우선순위

1. **Codebase** — 실제 동작하는 코드
2. **CLAUDE.md** — 이 문서 (프로젝트 규칙/컨벤션)
3. **docs/planning/** — 초기 서비스 기획/사양 고정본 (01-overview ~ 07-legal-compliance)
4. **docs/01-plan/, docs/02-design/, ...** — bkit PDCA 산출물 (기능별 계획/설계/분석/리포트)

### 3. No Guessing

모르는 것 → 문서 확인 → 없으면 사용자에게 질문 → 절대 추측 금지.

### 4. 면책 원칙 (서비스 핵심)

이 서비스는 **투자 자문이 아님**. 모든 UI/문구에 "참고용", "투자 판단과 책임은 사용자 본인" 원칙이 일관되게 반영되어야 함.

> **면책 집행 방식 (v0.8.x~)**: 실질 방어선은 **화면에 항상 노출되는 면책 고지**(`disclaimer-footer` + AI 카드 내 disclaimer 필드)이며, LLM에는 **프롬프트 수준 가이드**("매수/매도 자문·권유 금지")로 유도한다. 과거의 금칙어 substring 필터(`LegalGuardFilter`/`forbidden-terms.json`/CI Level 4 grep)는 정상 응답을 통째로 차단하는 오탐과 프롬프트 프라이밍 부작용이 커서 **제거**되었다. 자세한 내용은 `docs/planning/07-legal-compliance.md`.

---

## Current Status

**v0.3.0-beta — 운영 중.** FE(Vercel) + BE(Render) 배포 완료. 베타 서비스로 공개 운영.

### 완료된 주요 기능

- [x] MVP: 종목 시세 + 차트(OHLCV) + AI 참고 분석 + 뉴스
- [x] 시장 대시보드: 주요 지수(S&P500, Nasdaq, Dow, VIX) + USD/KRW
- [x] 인증: Supabase Auth (로그인/회원가입/비밀번호 재설정)
- [x] 북마크 + 알림 시스템
- [x] AI 참고 분석 v2 (RAG + 기술 지표)
- [x] AI 시그널 정합도 측정 인프라
- [x] 피드백 채널 (/feedback 페이지)
- [x] Yahoo Finance 마이그레이션 (TwelveData → Yahoo Finance 1차 전환)
- [x] 지금이니?! 리브랜딩 (로고/파비콘/테마 시스템)
- [x] 종목 상세 강화 (기업 정보, 시가총액, P/E, 52주 고저 등)
- [x] 대시보드 확장 (섹터 퍼포먼스, 매크로 지표, 카테고리 분류)

### 최근 완료

- [x] AI 시그널 UX 개선 (이중관점 단기/장기, 테마 보정)
- [x] API 캐시 최적화 (적응형 TTL + 2중 캐시)
- [x] 헤더 툴박스 + 플로팅 FAB + 스낵바 + 마이페이지 탭 리디자인
- [x] 라이트모드 UI 폴리싱 (에메랄드 배경, 스켈레톤 토큰 분리, 대시보드 카드 그룹화)

- [x] 매크로 상세 페이지 (차트 + 히스토리)
- [x] 애널리스트 평점/목표가 + 분기 실적

전체 로드맵: `docs/planning/06-roadmap.md`.

---

## Details (분리된 설정 파일)

@.claude/tech-stack.md
@.claude/conventions.md
@.claude/workflow.md

---

**Generated for**: 지금이니?! (Nowini)
**bkit Version**: 1.6.1
**Level**: Dynamic
**Phase**: v0.1.0 Beta (운영 중)
