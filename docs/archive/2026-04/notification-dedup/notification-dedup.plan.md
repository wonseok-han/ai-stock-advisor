---
template: plan
version: 1.2
feature: notification-dedup
date: 2026-04-20
author: wonseok-han
project: AI Stock Advisor
status: Draft
---

# notification-dedup Planning Document

> **Summary**: 가격 변동 푸시 알림의 중복 발송을 히스테리시스 + 쿨다운으로 억제한다.
>
> **Project**: AI Stock Advisor
> **Version**: Phase 4.5 post (Phase 5 pre)
> **Author**: wonseok-han
> **Date**: 2026-04-20
> **Status**: Draft

---

## Executive Summary

| Perspective | Content |
|-------------|---------|
| **Problem** | 가격 변동 임계값(예: ±5%)을 넘은 상태가 유지되면 15분 스케줄마다 동일 알림이 반복 발송되어 하루 최대 20+회 스팸이 발생함. 사용자가 알림을 꺼버리게 되어 서비스 가치가 무너짐. |
| **Solution** | (1) 히스테리시스: 트리거 = 임계값, 리셋 = 임계값 × 0.6. (2) 쿨다운: 동일 (user, ticker) 재발송 최소 간격 4h. DB에 발송 상태 필드 2개 추가 후 `NotificationCheckService`가 양 조건을 모두 통과해야만 발송. |
| **Function/UX Effect** | 동일 조건 알림이 하루 1~2회로 수렴. 조건 경계(4.5~5.5%)에서의 진동(flapping)에도 조용함. 리셋 이후 재돌파 시 정상적으로 1회 발송. |
| **Core Value** | 알림 신뢰성 회복 — "필요할 때만 온다"는 기대가 지켜져 사용자가 알림을 계속 켜 둘 수 있는 상태를 유지한다. |

---

## 1. Overview

### 1.1 Purpose

Phase 4.5에서 도입된 Web Push 가격 변동 알림이 **상태 기반 스팸**을 일으키는 구조적 결함을 해결한다. 임계값 돌파를 "이벤트(전이)" 개념으로 재정의하여, 조건 지속 상태에서 알림이 반복되지 않도록 한다.

### 1.2 Background

- 현재 구현(`NotificationCheckService.check()`)은 15분마다 모든 활성 setting을 순회하면서 임계값을 초과하면 **무조건** 푸시를 보냄 (`NotificationCheckService.java:38-68`, `checkPriceThreshold` @70-81).
- 중복 발송 방지 메커니즘 없음. `notification_settings` 테이블에도 상태 컬럼이 없음 (`V7__notification.sql:13-22`).
- 사용자 제보: "알림이 오는 건 확인했는데, 한 번 5% 돌파하면 계속 날아오는 거 아니냐?" — 그렇게 동작함.
- 단순 "돌파 시점만 발송" 방식은 임계값 주변 진동(flapping) 시 여전히 스팸이 됨. 따라서 히스테리시스가 필수.

### 1.3 Related Documents

- 알림 기능 원본 설계: `docs/archive/2026-04/phase4.5-improvements/`
- 현행 구현: `apps/api/src/main/java/com/aistockadvisor/notification/service/NotificationCheckService.java`
- DB 현행: `apps/api/src/main/resources/db/migration/V7__notification.sql`

---

## 2. Scope

### 2.1 In Scope

- [ ] `notification_settings` 테이블에 `last_notified_at`, `last_triggered_above` 컬럼 추가 (Flyway V8)
- [ ] `NotificationSettingEntity` / Repository에 상태 필드 반영
- [ ] `NotificationCheckService.checkPriceThreshold()` 로직 재작성 — 히스테리시스 + 쿨다운 판정
- [ ] 상수/설정화: 리셋 비율(기본 0.6), 쿨다운 시간(기본 4h) — `application.yml` 로 노출
- [ ] 기존 설정 업데이트 시(임계값 변경) 상태 리셋 처리
- [ ] BE 단위 테스트: 상태 전이 시나리오(미돌파/돌파/유지/리셋/재돌파/쿨다운 중)

### 2.2 Out of Scope

- FE UI 변경 없음 (알림 설정 화면은 그대로; 히스테리시스 파라미터는 서버 상수)
- 뉴스(`onNewNews`), 신호 변경(`onSignalChange`) 알림 — 아직 구현 안 됨. 향후 동일 패턴 적용은 별도 feature로.
- 일일 발송 횟수 cap — 쿨다운만으로 충분하다고 판단, 추후 필요 시 별도 feature.
- 사용자별 커스텀 쿨다운/히스테리시스 — YAGNI, 전역 상수로 시작.

---

## 3. Requirements

### 3.1 Functional Requirements

| ID | Requirement | Priority | Status |
|----|-------------|----------|--------|
| FR-01 | 임계값 최초 돌파 시에만 푸시 발송 (`last_triggered_above = false → true` 전이) | High | Pending |
| FR-02 | 발송 후 `|changePercent|` 이 리셋값(임계값 × 0.6) 아래로 내려가면 `last_triggered_above` 을 false로 리셋 | High | Pending |
| FR-03 | 리셋 후 재돌파해도 `last_notified_at` 기준 쿨다운(기본 4h) 내면 발송 보류 | High | Pending |
| FR-04 | 사용자가 임계값을 수정하면 상태(`last_triggered_above`, `last_notified_at`)를 리셋 — 신규 임계값으로 첫 돌파를 정상 감지 | Medium | Pending |
| FR-05 | 리셋 비율(기본 0.6), 쿨다운(기본 4h)은 `application.yml`의 `notification.dedup.*` 로 설정 가능 | Medium | Pending |
| FR-06 | 알림 미발송 케이스도 로그로 추적 가능 (디버그 레벨: "skipped: cooldown", "skipped: no transition") | Low | Pending |

### 3.2 Non-Functional Requirements

| Category | Criteria | Measurement Method |
|----------|----------|-------------------|
| Performance | 체크 사이클 추가 오버헤드 < 5ms/ticker (상태 필드 2개 read/write만 추가) | 기존 대비 벤치 없음; 쿼리 수 동일 확인 |
| Correctness | 경계 진동(4.9% ↔ 5.1%) 10회 반복 시 발송 1회 이하 | 단위 테스트 시나리오 |
| Backwards Compatibility | 기존 `notification_settings` 데이터는 마이그레이션 후 `last_triggered_above=false`, `last_notified_at=NULL`로 초기화 — 첫 체크 사이클에서 한 번은 정상 발송됨 | Flyway V8 DEFAULT 값 |
| Observability | skipped 사유별 로그 + 발송 시 기존 로그 유지 | `log.debug` 분기 |

---

## 4. Success Criteria

### 4.1 Definition of Done

- [ ] V8 마이그레이션 적용되고 기존 데이터 무결성 유지
- [ ] `NotificationCheckService` 상태 전이 기반 로직으로 교체
- [ ] 단위 테스트 6개 시나리오 통과 (미돌파 / 첫 돌파 / 유지 / 리셋 / 재돌파(쿨다운 내) / 재돌파(쿨다운 후))
- [ ] 실 환경에서 단일 종목을 임계값 근처에서 관찰해 중복 미발송 확인 (Zero Script QA)
- [ ] `./gradlew check` 통과

### 4.2 Quality Criteria

- [ ] 기존 lint/format 규칙 준수
- [ ] 새 상수는 `application.yml` + `@ConfigurationProperties` 로 주입 (하드코딩 지양)
- [ ] 트랜잭션 경계 명확화 — 발송 성공 후 상태 갱신(발송 실패 시 상태 변경 금지)

---

## 5. Risks and Mitigation

| Risk | Impact | Likelihood | Mitigation |
|------|--------|------------|------------|
| 푸시 발송 실패인데 상태만 "발송됨"으로 바뀌면 사용자가 중요 알림을 놓칠 수 있음 | High | Medium | `pushService.sendToUser()` 성공 반환 후에만 `last_notified_at`/`last_triggered_above` 업데이트. 현재 sendToUser 반환 타입 확인 필요 (없으면 boolean 반환으로 개선) |
| 임계값 변경 시 상태 리셋 누락 → 신규 임계값이 잘못 적용됨 | Medium | Medium | `NotificationSettingService.update()` 경로에서 임계값 변경을 감지해 상태 필드 초기화. 테스트로 보장 |
| 리셋 비율 0.6이 너무 관대/엄격할 수 있음 | Low | Low | `application.yml` 설정으로 뺐기 때문에 튜닝 가능. 초기값은 실 운영 2주 관찰 후 재조정 |
| 서버 재시작/배포 직후 첫 사이클에서 과거 돌파 상태를 "전이"로 오인 | Low | Low | `last_triggered_above` 이 이미 true면 발송 안 함 → 재시작 영향 없음 |
| DB 마이그레이션 중 운영 서비스 영향 | Low | Low | 컬럼 ADD with DEFAULT만 수행, lock 영향 미미 (PostgreSQL 11+ fast default) |

---

## 6. Architecture Considerations

### 6.1 Project Level Selection

| Level | Selected |
|-------|:--------:|
| Starter |  |
| **Dynamic** | ✅ |
| Enterprise |  |

기존 프로젝트 Level(Dynamic) 유지. 알림 도메인 단일 서비스 내부 변경이므로 레벨 전환 이슈 없음.

### 6.2 Key Architectural Decisions

| Decision | Options | Selected | Rationale |
|----------|---------|----------|-----------|
| 상태 저장소 | DB 컬럼 / Redis / In-memory | **DB 컬럼** | 쿨다운 기간(4h)이 Redis TTL보다 안전하고, 기존 `notification_settings` 와 1:1 대응. 재배포/장애 시 유실 없음 |
| 히스테리시스 비율 | 고정 0.6 / 설정화 | **설정화** | 실 운영 튜닝 필요성 높음. `application.yml` 주입 |
| 쿨다운 시간 | 1h / 4h / 24h | **4h (기본)** | 미국장 정규시간 6.5h 대비 장중 최대 2회 발송 허용. 설정 가능 |
| 리셋 판정 | 절대값 차 / 상대 비율 | **상대 비율** | 종목마다 임계값이 다를 수 있는 미래 확장성 고려 (현재는 전역이지만) |
| 상태 갱신 시점 | 발송 시도 직전 / 발송 성공 후 | **성공 후** | 실패 시 재시도 기회를 남김 |

### 6.3 Clean Architecture Approach

```
apps/api/src/main/java/com/aistockadvisor/notification/
├── controller/         (변경 없음)
├── domain/             (변경 없음 — 외부 API 응답 DTO)
├── infra/
│   ├── NotificationSettingEntity.java     [수정: 2 필드 추가]
│   └── NotificationSettingRepository.java (변경 없음 — JPA 표준)
└── service/
    ├── NotificationCheckService.java       [수정: 히스테리시스 + 쿨다운]
    ├── NotificationSettingService.java     [수정: 임계값 변경 시 상태 리셋]
    └── PushService.java                    [검토: 발송 성공 여부 반환 여부]

apps/api/src/main/resources/
├── application.yml                         [수정: notification.dedup.* 추가]
└── db/migration/
    └── V8__notification_dedup.sql          [신규]
```

---

## 7. Convention Prerequisites

### 7.1 Existing Project Conventions

- [x] `CLAUDE.md` coding conventions (BE 섹션) 존재
- [x] Java 패키지 컨벤션: `com.aistockadvisor.{domain}` (notification 도메인 내부 수정)
- [x] Flyway migration 규칙: `V{n}__{snake_case}.sql`
- [x] `@ConfigurationProperties` 패턴이 프로젝트에 이미 사용 중인지 확인 필요 — 없으면 이번에 도입

### 7.2 Conventions to Define/Verify

| Category | Current State | To Define | Priority |
|----------|---------------|-----------|:--------:|
| Flyway version | V7까지 존재 | **V8** 사용 | High |
| `application.yml` 섹션 키 | notification 섹션 존재 여부 확인 필요 | `notification.dedup.resetRatio`, `notification.dedup.cooldownHours` | High |
| 설정 주입 방식 | 확인 필요 | `@ConfigurationProperties(prefix = "notification.dedup")` | Medium |

### 7.3 Environment Variables Needed

없음 — 모든 설정은 `application.yml` 정적 값. 환경별 override 는 표준 Spring profile 메커니즘으로 커버.

### 7.4 Pipeline Integration

해당 없음 — 기존 Phase 4.5 기능의 버그성 개선. 9-phase 파이프라인 외 feature patch.

---

## 8. Next Steps

1. [ ] Design 문서 작성 (`/pdca design notification-dedup`) — 상태 전이 다이어그램 + 테스트 시나리오 상세화
2. [ ] `feat/notification-dedup` 브랜치 생성 (Phase 2+ PR 정책에 따름)
3. [ ] 구현 (V8 migration → Entity → Service → Test)
4. [ ] Zero Script QA로 경계 진동 케이스 관찰
5. [ ] PR 생성 → squash merge

---

## Version History

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 0.1 | 2026-04-20 | Initial draft — 히스테리시스 + 쿨다운 방식 확정 | wonseok-han |
