package com.aistockadvisor.notification.infra;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationSettingEntityTest {

    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 4, 20, 10, 0, 0, 0, ZoneOffset.UTC);

    private NotificationSettingEntity entityWithState() {
        NotificationSettingEntity e = new NotificationSettingEntity(UUID.randomUUID(), "AAPL");
        e.update(new BigDecimal("5"), false, true);
        e.markNotified(NOW);
        return e;
    }

    @Test
    @DisplayName("U1: 임계값 변경 시 상태(lastTriggeredAbove, lastNotifiedAt)가 리셋된다")
    void u1_thresholdChangedResetsState() {
        NotificationSettingEntity e = entityWithState();
        assertThat(e.isLastTriggeredAbove()).isTrue();
        assertThat(e.getLastNotifiedAt()).isNotNull();

        e.update(new BigDecimal("7"), false, true);

        assertThat(e.isLastTriggeredAbove()).isFalse();
        assertThat(e.getLastNotifiedAt()).isNull();
    }

    @Test
    @DisplayName("U2: 임계값 동일값 재저장 시 상태가 유지된다")
    void u2_sameThresholdPreservesState() {
        NotificationSettingEntity e = entityWithState();

        e.update(new BigDecimal("5"), true, true);

        assertThat(e.isLastTriggeredAbove()).isTrue();
        assertThat(e.getLastNotifiedAt()).isEqualTo(NOW);
        assertThat(e.isOnNewNews()).isTrue();
    }

    @Test
    @DisplayName("U3: enabled 플래그만 토글해도 상태는 유지된다")
    void u3_otherFieldsPreserveState() {
        NotificationSettingEntity e = entityWithState();

        e.update(new BigDecimal("5"), false, false);

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
        e.update(new BigDecimal("5"), false, true);
        assertThat(e.isLastTriggeredAbove()).isFalse();

        e.markNotified(NOW);

        assertThat(e.isLastTriggeredAbove()).isTrue();
        assertThat(e.getLastNotifiedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("U6: markNewsNotified 는 lastNewsPublishedAt 만 설정한다")
    void u6_markNewsNotifiedSetsWatermark() {
        NotificationSettingEntity e = new NotificationSettingEntity(UUID.randomUUID(), "AAPL");
        Instant t0 = Instant.parse("2026-04-20T09:30:00Z");
        assertThat(e.getLastNewsPublishedAt()).isNull();

        e.markNewsNotified(t0);

        assertThat(e.getLastNewsPublishedAt()).isEqualTo(t0);
    }

    @Test
    @DisplayName("U7: update() 는 lastNewsPublishedAt 을 건드리지 않는다 (가격 상태와 독립)")
    void u7_updatePreservesNewsWatermark() {
        NotificationSettingEntity e = entityWithState();
        Instant newsAt = Instant.parse("2026-04-20T09:30:00Z");
        e.markNewsNotified(newsAt);

        // 임계값 변경 (가격 상태는 리셋되지만 뉴스 watermark 는 유지)
        e.update(new BigDecimal("7"), false, true);
        assertThat(e.isLastTriggeredAbove()).isFalse();
        assertThat(e.getLastNotifiedAt()).isNull();
        assertThat(e.getLastNewsPublishedAt()).isEqualTo(newsAt);

        // 동일 임계값 재저장 (가격 상태 유지, 뉴스 watermark 유지)
        e.update(new BigDecimal("7"), true, false);
        assertThat(e.getLastNewsPublishedAt()).isEqualTo(newsAt);
    }
}
