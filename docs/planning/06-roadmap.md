# 06. 단계별 로드맵

## Phase 0: 기획 (현재 단계) 🟡

- [x] 서비스 포지셔닝 결정 (A안)
- [x] 기술 스택 결정 (Next.js + Spring Boot)
- [x] 데이터 소스 선정
- [x] AI 전략 수립
- [ ] MVP 기능 명세 확정 (이 문서들 리뷰 후)
- [ ] DB 스키마 최종 확정
- [ ] 와이어프레임 / 화면 설계 (대략)
- [ ] 법적 고지 문구 확정

**완료 조건:** 개발에 들어가도 되겠다는 스스로의 확신.

---

## Phase 1: MVP 코어 데이터 파이프라인

**목표:** 로그인 없이, 한 개 종목에 대한 **정적/동적 데이터를 보여주는 것**.

### 백엔드 (Spring Boot)
- [ ] 프로젝트 초기화 (Gradle, Java 21)
- [ ] 공통 설정 (Spring Security, CORS, Actuator, Flyway)
- [ ] `ExternalApiClient` 추상화 (Finnhub, Yahoo fallback)
- [ ] Redis 캐시 설정
- [ ] Rate limiter (Bucket4j) 연동
- [ ] API: `GET /api/stocks/search?q=`
- [ ] API: `GET /api/stocks/{ticker}/profile`
- [ ] API: `GET /api/stocks/{ticker}/quote`
- [ ] API: `GET /api/stocks/{ticker}/candles?resolution=D&range=1Y`
- [ ] API: `GET /api/stocks/{ticker}/indicators` (ta4j 계산)
- [ ] API: `GET /api/stocks/{ticker}/news`
- [ ] 기본 에러 처리 + 로깅

### 프론트엔드 (Next.js)
- [ ] 프로젝트 초기화 (App Router, TS, Tailwind)
- [ ] 레이아웃 + 네비게이션
- [ ] 검색 바 (티커/회사명 자동완성)
- [ ] 종목 상세 페이지
  - [ ] 기본 정보 카드
  - [ ] 차트 (TradingView Lightweight, 캔들+MA+거래량)
  - [ ] 기술적 지표 카드 (툴팁 해설 포함)
  - [ ] 뉴스 리스트 (원문)
- [ ] 로딩/에러 상태 UI
- [ ] **면책 고지 컴포넌트 (전역 footer)**

### 배포
- [ ] Vercel FE 연결
- [ ] Fly.io or Oracle Cloud BE 배포
- [ ] Supabase DB 프로젝트 생성
- [ ] Upstash Redis 연결

**완료 조건:** 배포된 URL에서 `AAPL` 검색하면 차트+지표+뉴스가 보임.

---

## Phase 2: AI 레이어

**목표:** 종목 상세 페이지에 **AI 분석 카드** 추가.

### 백엔드
- [ ] `LlmClient` 인터페이스 (Gemini 구현체)
- [ ] 프롬프트 빌더 (종목 데이터 → 프롬프트 컨텍스트)
- [ ] API: `GET /api/stocks/{ticker}/ai-analysis`
- [ ] JSON 응답 파싱/검증 로직
- [ ] Postgres + Redis hybrid 캐시 (1시간)
- [ ] 뉴스 번역/요약 파이프라인 추가
- [ ] API 응답에 번역된 뉴스 포함

### 프론트엔드
- [ ] AI 분석 카드 컴포넌트
  - 시그널 배지, 신뢰도 게이지
  - 근거/리스크 리스트
  - 생성 시각 + "새로고침" 버튼 (캐시 무효화는 서버가 통제)
- [ ] 뉴스 번역된 버전으로 표시 (원문 토글)

**완료 조건:** AI 시그널이 표시되고, 다양한 종목에서 합리적인 결과가 나옴.

---

## Phase 3: 시장 대시보드

**목표:** 메인 페이지에 **시장 전체 스냅샷**.

### 백엔드
- [ ] API: `GET /api/market/overview`
  - 주요 지수, VIX, USD/KRW, 10Y 금리
- [ ] API: `GET /api/market/news`
- [ ] API: `GET /api/market/movers` (gainers/losers/인기)
- [ ] 캐시 전략 적용

### 프론트엔드
- [ ] 메인(`/`) 페이지 — 대시보드 위젯 나열
- [ ] 지수 카드, VIX 게이지, 환율 위젯
- [ ] 시장 뉴스 피드
- [ ] 인기/급등락 종목 리스트 → 클릭하면 종목 상세로

**완료 조건:** 첫 화면이 "오늘 시장 어때?"에 대한 답이 됨.

---

## Phase 4: 회원 / 북마크 / 알림

**목표:** 개인화.

### 인증
- [ ] Supabase Auth 프론트 연동 (이메일 + 구글 OAuth)
- [ ] Spring Boot JWT 검증 (Resource Server)
- [ ] `/api/me` 엔드포인트

### 북마크
- [ ] DB: `bookmarks` 테이블
- [ ] API: `POST /api/bookmarks`, `DELETE /api/bookmarks/{ticker}`, `GET /api/bookmarks`
- [ ] 종목 페이지에 북마크 토글
- [ ] 마이페이지 (북마크 목록 + 간단 스냅샷)

### 알림 (Web Push)
- [ ] Service Worker 등록
- [ ] 푸시 구독/해지 플로우
- [ ] DB: `push_subscriptions`, `notification_settings`
- [ ] Spring Boot 스케줄러: 15분마다 북마크 종목 체크
- [ ] 알림 조건 평가 (가격 변동, 새 뉴스, 시그널 변화)
- [ ] Web Push 전송 (VAPID 키 사용)

**완료 조건:** 사용자가 북마크한 종목에 변화가 생기면 브라우저 알림이 뜬다.

---

## Phase 5+: 향후 과제 (미확정)

- 모바일 앱 (React Native or Flutter + FCM)
- 포트폴리오 시뮬레이션 (가상 매매)
- 백테스팅
- 고급 지표 (이치모쿠, 피보나치, Elliott Wave)
- 소셜/커뮤니티
- 수익화 (프리미엄, 제휴)
- 실시간 스트리밍 시세 (Polygon.io 유료)
- 한국 주식 확장

---

## 마일스톤 체크리스트 (한 줄 요약)

| Phase | 한 줄 목표 |
|---|---|
| 0 | "이 문서로 개발 시작해도 되겠다" |
| 1 | "AAPL 페이지가 차트+지표+뉴스로 채워진다" |
| 2 | "AI가 AAPL에 대해 말이 되는 소리를 한다" |
| 3 | "메인에서 오늘 시장 분위기가 한눈에 보인다" |
| 4 | "내가 찜한 종목에 변화가 생기면 알림이 온다" |
| 5+ | "사람들이 실제로 쓰고, 다음을 고민한다" |
