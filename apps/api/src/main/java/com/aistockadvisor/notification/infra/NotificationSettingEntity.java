package com.aistockadvisor.notification.infra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
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
    private boolean onSignalChange;

    @Column(nullable = false)
    private boolean enabled = true;

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
    public boolean isOnSignalChange() { return onSignalChange; }
    public boolean isEnabled() { return enabled; }

    public void update(BigDecimal priceChangeThreshold, boolean onNewNews, boolean onSignalChange, boolean enabled) {
        this.priceChangeThreshold = priceChangeThreshold;
        this.onNewNews = onNewNews;
        this.onSignalChange = onSignalChange;
        this.enabled = enabled;
    }
}
