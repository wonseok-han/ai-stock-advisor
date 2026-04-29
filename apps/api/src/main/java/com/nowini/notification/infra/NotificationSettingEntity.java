package com.nowini.notification.infra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "notification_settings")
public class NotificationSettingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 16)
    private String ticker;

    private BigDecimal priceChangeThreshold;

    @Column(nullable = false)
    private boolean onNewNews;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "last_notified_at")
    private OffsetDateTime lastNotifiedAt;

    @Column(name = "last_triggered_above", nullable = false)
    private boolean lastTriggeredAbove = false;

    @Column(name = "last_news_published_at")
    private Instant lastNewsPublishedAt;

    protected NotificationSettingEntity() {
    }

    public NotificationSettingEntity(UUID userId, String ticker) {
        this.userId = userId;
        this.ticker = ticker.toUpperCase();
    }

    public Long getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getTicker() { return ticker; }
    public BigDecimal getPriceChangeThreshold() { return priceChangeThreshold; }
    public boolean isOnNewNews() { return onNewNews; }
    public boolean isEnabled() { return enabled; }
    public OffsetDateTime getLastNotifiedAt() { return lastNotifiedAt; }
    public boolean isLastTriggeredAbove() { return lastTriggeredAbove; }
    public Instant getLastNewsPublishedAt() { return lastNewsPublishedAt; }

    /**
     * 사용자 설정 업데이트. 임계값이 변경되면 발송 상태를 리셋하여
     * 신규 임계값으로 첫 돌파를 정상 감지하도록 한다.
     */
    public void update(BigDecimal priceChangeThreshold, boolean onNewNews, boolean enabled) {
        boolean thresholdChanged = !Objects.equals(this.priceChangeThreshold, priceChangeThreshold);
        this.priceChangeThreshold = priceChangeThreshold;
        this.onNewNews = onNewNews;
        this.enabled = enabled;
        if (thresholdChanged) {
            this.lastTriggeredAbove = false;
            this.lastNotifiedAt = null;
        }
    }

    /** 푸시 발송 성공 시 호출. 상태를 "위 + 발송됨"으로 전이시킨다. */
    public void markNotified(OffsetDateTime now) {
        this.lastNotifiedAt = now;
        this.lastTriggeredAbove = true;
    }

    /** 리셋 임계값 아래로 내려갔을 때 호출. 쿨다운 타이머는 유지. */
    public void resetTrigger() {
        this.lastTriggeredAbove = false;
    }

    /**
     * 뉴스 알림 watermark 전진. 푸시 발송 성공 또는 baseline 세팅 시 호출.
     * @param publishedAt 최신 뉴스의 published_at
     */
    public void markNewsNotified(Instant publishedAt) {
        this.lastNewsPublishedAt = publishedAt;
    }
}
