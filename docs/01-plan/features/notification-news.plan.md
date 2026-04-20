---
template: plan
version: 1.0
feature: notification-news
date: 2026-04-20
author: wonseok-han
project: AI Stock Advisor
status: Draft
---

# notification-news Plan

## Executive Summary

| 관점 | 요약 |
|---|---|
| **Problem** | `onNewNews` 토글이 UI(알림 설정 모달)·DTO·Entity·DB 컬럼으로 존재하지만 실제 발송 로직은 없는 **죽은 토글**. 사용자가 "새 뉴스 발생 시" 를 켜도 아무 일도 일어나지 않아 기대 배반. 또한 관심 종목의 시황을 놓치지 않으려면 사용자가 매번 앱을 열어 뉴스 섹션을 확인해야 함. |
| **Solution** | 기존 15분 스케줄러(`NotificationCheckService`)에 `checkNewNews()` 를 추가하고, 기존 `NewsService.getNews()` 를 재사용해 Finnhub→DB 캐시→LLM 번역 파이프라인을 그대로 활용. Watermark 방식(`last_news_published_at`) 으로 "이전 최신 뉴스 시각 이후 published 된 기사만" 발송. LLM 비용은 기존 24h 번역 캐시로 상쇄. Web Push payload 에 `url` 필드 추가해 알림 탭 시 해당 종목 상세로 이동. |
| **Function UX Effect** | 관심 종목 알림을 설정해둔 사용자는 15분 이내에 신규 뉴스를 한국어 제목으로 푸시 수신. 앱을 열지 않아도 핵심 이벤트(실적·규제·M&A 등) 발생 시점을 인지 가능. 토글이 "실제 동작하는 기능" 이 되어 UI 약속 이행. |
| **Core Value** | "참고용 분석 도구" 포지셔닝에 맞는 저비용·고가치 알림. LLM 배치 호출 없이 기존 on-demand 번역 캐시 재사용으로 토큰 비용을 **0~최소** 수준으로 유지(user facing 뉴스 화면 조회 시 이미 번역된 경우 cost=0). 북마크 → 알림 → 뉴스 → 종목 상세 로 이어지는 사용자 리텐션 고리 완성. |

## 1. Goal

- **G1 (기능 구현)**: 북마크 + 알림 설정 + `onNewNews=true` 인 사용자에게 15분 주기로 신규 뉴스 1건(또는 요약)을 Web Push 로 발송.
- **G2 (중복 억제)**: Watermark(`last_news_published_at`) 기반으로 "이미 알린 뉴스" 재발송 차단. 첫 활성화 시 baseline 설정으로 과거 뉴스 플러시 방지.
- **G3 (클릭 이동)**: 알림 탭 시 해당 종목 상세 페이지(`/stocks/{ticker}`) 또는 기사 URL 로 이동 (Service Worker 측 NotificationClick 핸들러 확장).
- **G4 (비용 최소화)**: 기존 `NewsService.getNews()` 파이프라인 재사용 → LLM 번역은 24h 캐시로 중복 호출 억제.

## 2. Non-Goals

- **AI 시그널 변화 알림**: YAGNI — `notification-ui-cleanup` 에서 이미 삭제. 별도 feature(`notification-signal`) 필요 시 재도입.
- **뉴스 개인화/랭킹**: Finnhub 최신순 그대로. sentiment·관련도 스코어링은 추후 feature.
- **사용자별 뉴스 알림 빈도 커스터마이징**: 시스템 전역 15분 사이클만 (기존 스케줄러 재사용).
- **뉴스 요약 품질 개선 (LLM 재튜닝)**: 기존 `NewsTranslator` 그대로. 별도 feature.
- **푸시 알림 링크 UTM/analytics**: MVP 범위 아님. 단순 URL 전달만.
- **다국어 뉴스 원문**: 기존 `titleKo` fallback → `titleEn` 정책 유지.

## 3. Requirements

### 3.1 Functional Requirements

| FR | 요구사항 | 수용 기준 |
|----|---------|-----------|
| FR-01 | `onNewNews=true` + `enabled=true` 인 설정에 대해 15분 주기로 뉴스 신규 발생 여부 체크 | `NotificationCheckService.check()` 에 `checkNewNews(setting)` 호출 추가 |
| FR-02 | `last_news_published_at` 이전 뉴스는 발송 대상 아님 | Watermark 비교로 `publishedAt > last_news_published_at` 만 필터링 |
| FR-03 | 신규 활성화 시 baseline 설정 (과거 뉴스 flood 방지) | `last_news_published_at = NULL` → 첫 사이클에서 "최신 뉴스 publishedAt" 으로 세팅만 하고 발송 안 함 |
| FR-04 | 신규 뉴스가 2건 이상이면 최신 1건 headline + "외 N건" 표기 | push body 포맷: `{titleKo} 외 N건` (N = newCount - 1) |
| FR-05 | Web Push payload 에 `url` 필드 포함 | payload 스키마: `{title, body, icon, url}`, Service Worker 에서 `event.waitUntil(clients.openWindow(url))` |
| FR-06 | 알림 클릭 시 종목 상세 페이지로 이동 (기본) | `url = "/stocks/{ticker}"` — 기사 직접 링크 아님(여러 기사 대표 이동) |
| FR-07 | 발송 성공 시에만 watermark 전진 | `PushService.sendToUser()` = true 일 때만 `setting.markNewsNotified(publishedAt)` 호출 (기존 fail-safe 패턴 재사용) |
| FR-08 | `onNewNews=false` 또는 `enabled=false` 면 체크 스킵 | 기존 필터 조건에 `isOnNewNews()` 추가 |
| FR-09 | 뉴스 fetch 실패 시 사이클 정상 진행 | 기존 try-catch 재사용 — 로그만 남기고 다른 종목/설정 계속 처리 |

### 3.2 Non-Functional Requirements

| NFR | 요구사항 |
|-----|---------|
| NFR-01 | 기존 가격 알림 dedup(`NotificationDedupPolicy`) 불변 — 뉴스 알림은 별도 메서드/상태 컬럼 |
| NFR-02 | LLM 호출 비용 증가 억제 — `NewsService.getNews()` 의 24h 번역 캐시 재사용. notification-only 경로에서 추가 LLM 트래픽 없음이 이상적 |
| NFR-03 | 스케줄러 사이클 당 뉴스 fetch 횟수 ≤ 설정된 unique ticker 수 (기존 price check 와 fetch 공유 고려) |
| NFR-04 | `./gradlew check` 통과 — 신규 unit tests 추가 (Entity U-news, Policy-news if extracted) |
| NFR-05 | Flyway V12 migration — `ADD COLUMN IF NOT EXISTS last_news_published_at TIMESTAMPTZ`, 멱등 |
| NFR-06 | Service Worker 업데이트로 기존 알림(가격) 도 `url` 처리 가능해야 함 — 백워드 호환 (`url` 없으면 기본 동작) |
| NFR-07 | PR 1개로 squash merge (feat/notification-news → develop) |

## 4. Scope & Impact

### 4.1 BE 변경 파일

| 파일 | 변경 |
|------|------|
| `apps/api/src/main/resources/db/migration/V12__notification_news.sql` | **신규** — `ALTER TABLE notification_settings ADD COLUMN IF NOT EXISTS last_news_published_at TIMESTAMPTZ` |
| `apps/api/src/main/java/.../notification/infra/NotificationSettingEntity.java` | 필드 `lastNewsPublishedAt` (Instant) + getter + `markNewsNotified(Instant)` 메서드 |
| `apps/api/src/main/java/.../notification/service/NotificationCheckService.java` | `check()` 루프 내 `checkNewNews(setting)` 호출 추가, 신규 메서드 구현 (NewsService 주입) |
| `apps/api/src/main/java/.../notification/service/NotificationNewsDedupPolicy.java` | **신규 (optional)** — 순수 함수 `shouldNotify(latestNews, lastNewsPublishedAt)` 추출. 단순하므로 Service 내부 헬퍼로 둘 수도 있음 |
| `apps/api/src/main/java/.../notification/service/PushService.java` | payload 에 `url` 필드 추가 (nullable), 기존 호출부 호환 |
| `apps/api/src/test/java/.../notification/infra/NotificationSettingEntityTest.java` | U6: `markNewsNotified()` 동작 검증 추가 |
| `apps/api/src/test/java/.../notification/service/NotificationNewsDedupPolicyTest.java` | **신규 (optional)** — 3~4 시나리오 |

### 4.2 FE 변경 파일

| 파일 | 변경 |
|------|------|
| `apps/web/public/sw.js` | `notificationclick` 이벤트에서 `event.notification.data?.url` 있으면 `clients.openWindow(url)` |
| (선택) `apps/web/src/types/notification.ts` | 변경 없음 — `onNewNews` 이미 존재 |
| (선택) `apps/web/src/features/stock-detail/notification-setting-modal.tsx` | 변경 없음 — 토글 이미 존재 |

### 4.3 영향받지 않는 부분

- `NotificationDedupPolicy` (가격 히스테리시스) — 뉴스 알림은 독립 경로
- `NotificationSettingEntity.update()` — 3-arg 시그니처 유지 (`onNewNews` 이미 파라미터)
- FE notification 관련 훅/컴포넌트 — UI 변경 불필요
- `NewsController`, `NewsService.getNews()` — 재사용만, 수정 없음

### 4.4 재사용 대상

- `NewsService.getNews(ticker, limit=1)` — 최신 번역 뉴스 조회(없으면 Finnhub fetch + 번역)
- `PushService.sendToUser()` — 발송 (payload 스키마만 확장)
- `NotificationSettingEntity.isEnabled()`, `isOnNewNews()` — 필터링

## 5. Risks

| 리스크 | 영향 | 완화 |
|--------|-----|------|
| LLM 번역 비용 증가 (사용자 앱 미접속 시 notification cycle 이 번역 트리거) | 토큰 비용 | `NewsService.getNews()` 는 24h 캐시 우선. 사용자 앱 접속이 있는 종목은 이미 캐시 적중. 최악의 경우: 사용자 N 명 × 활성 티커 × 15분/사이클 × 5 articles ≤ 기존 뉴스 화면 조회 트래픽 수준. 실측 후 NFR 위반 시 "제목만 번역" 옵션 검토 |
| 첫 활성화 시 과거 뉴스 다수 발송 (flood) | UX 스팸 | FR-03 baseline 설정 — `last_news_published_at IS NULL` → 첫 사이클은 watermark만 세팅하고 발송 skip |
| Finnhub rate limit 위반 (가격 + 뉴스 동시 fetch) | 부분 실패 | 기존 `QuoteService.getQuote()` 와 `NewsService.getNews()` 각자 캐시 보유. NotificationCheckService 가 티커별로 한 번씩만 호출 |
| Web Push payload 크기 제한 (4KB) 근접 | 큰 제목/요약 잘림 | FR-04 body 최대 200자로 truncate. Finnhub headline 은 대부분 100자 이하 |
| 사용자가 앱 미설치 상태에서 URL 클릭 | 라우팅 실패 | `/stocks/{ticker}` 는 public route. 로그인 필요 없음 |
| Service Worker 배포 지연 (캐시) | 구형 SW 가 `url` 무시 | payload 에 `url` 있어도 구형 SW 는 무시하고 기본 동작. 기능 저하만 있음, 에러 없음 |

## 6. Success Criteria

- [ ] `notification_settings.last_news_published_at` 컬럼 존재 (V12 적용 확인)
- [ ] 테스트용 ticker 에 알림 설정(`onNewNews=true`) 후 수동 트리거 시 Web Push 수신
- [ ] 첫 활성화 사이클은 push 없고 watermark 만 저장 (log 확인)
- [ ] 2번째 사이클에 신규 뉴스 있으면 push 1회 수신
- [ ] 3번째 사이클 (신규 뉴스 없음) → push 없음
- [ ] 알림 클릭 시 `/stocks/{ticker}` 페이지로 이동
- [ ] `./gradlew check` BUILD SUCCESSFUL
- [ ] 기존 가격 알림(Dedup Policy) 회귀 테스트 Green 유지 (T1~T9)
- [ ] PR 생성 + squash merge 완료

## 7. Implementation Order

1. Plan + Design 문서 작성 (본 문서 + `/pdca design notification-news`)
2. BE: Entity 필드 추가 + `markNewsNotified()` + EntityTest U6
3. BE: Flyway V12 migration
4. BE: `NotificationCheckService.checkNewNews()` — baseline skip + watermark send
5. BE: `PushService` payload `url` 필드 추가
6. BE: (선택) `NotificationNewsDedupPolicy` 순수 함수 추출 + Policy test
7. FE: `public/sw.js` `notificationclick` 에서 `url` 처리
8. `./gradlew check` + `make web-check`
9. 수동 QA: 테스트 유저로 뉴스 발생 시나리오 관찰 (Zero Script QA)
10. 커밋 → push → PR → squash merge → archive

---

## Version History

| Version | Date | Changes | Author |
|---|---|---|---|
| 1.0 | 2026-04-20 | 초기 Plan — watermark 기반 dedup, 기존 NewsService 재사용, Web Push url 확장 | wonseok-han |
