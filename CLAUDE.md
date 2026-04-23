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

이 서비스는 **투자 자문이 아님**. 모든 UI/API/문구에 "참고용", "투자 판단과 책임은 사용자 본인" 원칙이 일관되게 반영되어야 함. 자세한 내용은 `docs/planning/07-legal-compliance.md`.

---

## Current Status

**v0.1.0 Beta — 운영 중.** FE(Vercel) + BE(Fly.io) 배포 완료. 베타 서비스로 공개 운영.

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

### 진행 예정

- [ ] 종목 상세 강화 (기업 정보, 시가총액, P/E, 52주 고저 등)
- [ ] 애널리스트 평점/목표가 + 분기 실적
- [ ] 대시보드 확장 (섹터 퍼포먼스, 국채, 원자재 등)
- [ ] AI 신호 UX 명확화
- [ ] 헤더 툴박스 (Command Palette)

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
