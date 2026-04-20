package com.aistockadvisor.notification.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * 가격 변동 푸시 알림 중복 억제 정책. 순수 함수 — DI·Spring 의존 없음.
 *
 * 규칙:
 *   1. 히스테리시스: trigger = threshold, reset = threshold * resetRatio.
 *      reset 아래로 내려가야 "발송 가능" 상태로 복귀.
 *   2. 쿨다운: 마지막 발송 시각으로부터 최소 간격이 지나야 재발송.
 *   3. 발송은 "아래 → 위" 전이 시점에만 후보.
 *
 * 반환 Decision.action 분류:
 *   - SEND                 : 발송 대상. 발송 성공 시 호출측이 상태를 markNotified.
 *   - RESET_ONLY           : 리셋 임계값 아래로 하락 — lastTriggeredAbove만 false로.
 *   - SKIP_NO_TRANSITION   : 이미 "위" 상태 유지 중 — 발송 안 함.
 *   - SKIP_COOLDOWN        : 전이 감지했으나 쿨다운 기간 내 — 발송 안 함.
 *   - NOOP                 : 상태 변경 없음 (리셋-트리거 사이 구간 등).
 */
public final class NotificationDedupPolicy {

    private NotificationDedupPolicy() {}

    public enum Action { SEND, SKIP_NO_TRANSITION, SKIP_COOLDOWN, RESET_ONLY, NOOP }

    public record Decision(Action action, String reason) {}

    public static Decision decide(
            BigDecimal absChange,
            BigDecimal threshold,
            BigDecimal resetRatio,
            Duration cooldown,
            boolean lastTriggeredAbove,
            OffsetDateTime lastNotifiedAt,
            OffsetDateTime now
    ) {
        BigDecimal resetThreshold = threshold.multiply(resetRatio);
        boolean aboveTrigger = absChange.compareTo(threshold) >= 0;
        boolean belowReset = absChange.compareTo(resetThreshold) < 0;

        if (aboveTrigger && !lastTriggeredAbove) {
            if (lastNotifiedAt != null
                    && Duration.between(lastNotifiedAt, now).compareTo(cooldown) < 0) {
                return new Decision(Action.SKIP_COOLDOWN,
                        "cooldown active: last=" + lastNotifiedAt);
            }
            return new Decision(Action.SEND, "transition below->above");
        }

        if (aboveTrigger) {
            return new Decision(Action.SKIP_NO_TRANSITION,
                    "still above, already notified");
        }

        if (belowReset && lastTriggeredAbove) {
            return new Decision(Action.RESET_ONLY,
                    "dropped below reset threshold");
        }

        return new Decision(Action.NOOP,
                "between reset and trigger, no change");
    }
}
