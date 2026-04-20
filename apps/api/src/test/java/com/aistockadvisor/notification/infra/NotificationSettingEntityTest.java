package com.aistockadvisor.notification.infra;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationSettingEntityTest {

    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 4, 20, 10, 0, 0, 0, ZoneOffset.UTC);

    private NotificationSettingEntity entityWithState() {
        NotificationSettingEntity e = new NotificationSettingEntity(UUID.randomUUID(), "AAPL");
        e.update(new BigDecimal("5"), false, false, true);
        e.markNotified(NOW);
        return e;
    }

    @Test
    @DisplayName("U1: 임계값 변경 시 상태(lastTriggeredAbove, lastNotifiedAt)가 리셋된다")
    void u1_thresholdChangedResetsState() {
        NotificationSettingEntity e = entityWithState();
        assertThat(e.isLastTriggeredAbove()).isTrue();
        assertThat(e.getLastNotifiedAt()).isNotNull();

        e.update(new BigDecimal("7"), false, false, true);

        assertThat(e.isLastTriggeredAbove()).isFalse();
        assertThat(e.getLastNotifiedAt()).isNull();
    }

    @Test
    @DisplayName("U2: 임계값 동일값 재저장 시 상태가 유지된다")
    void u2_sameThresholdPreservesState() {
        NotificationSettingEntity e = entityWithState();

        e.update(new BigDecimal("5"), true, true, true);

        assertThat(e.isLastTriggeredAbove()).isTrue();
        assertThat(e.getLastNotifiedAt()).isEqualTo(NOW);
        assertThat(e.isOnNewNews()).isTrue();
        assertThat(e.isOnSignalChange()).isTrue();
    }

    @Test
    @DisplayName("U3: enabled 플래그만 토글해도 상태는 유지된다")
    void u3_otherFieldsPreserveState() {
        NotificationSettingEntity e = entityWithState();

        e.update(new BigDecimal("5"), false, false, false);

        assertThat(e.isLastTriggeredAbove()).isTrue();
        assertThat(e.getLastNotifiedAt()).isEqualTo(NOW);
        assertThat(e.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("U4: resetTrigger 는 lastNotifiedAt 을 건드리지 않는다 (쿨다운 유지)")
    void u4_resetTriggerKeepsTimestamp() {
        NotificationSettingEntity e = entityWithState();

        e.resetTrigger();

        assertThat(e.isLastTriggeredAbove()).isFalse();
        assertThat(e.getLastNotifiedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("U5: markNotified 는 타임스탬프 + lastTriggeredAbove 를 함께 설정한다")
    void u5_markNotifiedSetsBoth() {
        NotificationSettingEntity e = new NotificationSettingEntity(UUID.randomUUID(), "AAPL");
        e.update(new BigDecimal("5"), false, false, true);
        assertThat(e.isLastTriggeredAbove()).isFalse();

        e.markNotified(NOW);

        assertThat(e.isLastTriggeredAbove()).isTrue();
        assertThat(e.getLastNotifiedAt()).isEqualTo(NOW);
    }
}
