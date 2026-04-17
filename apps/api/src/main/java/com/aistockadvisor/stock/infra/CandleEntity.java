package com.aistockadvisor.stock.infra;

import com.aistockadvisor.stock.domain.Candle;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * 일봉 OHLCV + adjusted close JPA 엔티티.
 * PK: (ticker, tradeDate). Yahoo Finance SoR.
 * 참조: docs/02-design/features/phase4.5-improvements.design.md §3.2
 */
@Entity
@Table(name = "candles")
@IdClass(CandleId.class)
public class CandleEntity {

    @Id
    @Column(name = "ticker", length = 10, nullable = false)
    private String ticker;

    @Id
    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(precision = 12, scale = 4, nullable = false)
    private BigDecimal open;

    @Column(precision = 12, scale = 4, nullable = false)
    private BigDecimal high;

    @Column(precision = 12, scale = 4, nullable = false)
    private BigDecimal low;

    @Column(precision = 12, scale = 4, nullable = false)
    private BigDecimal close;

    @Column(name = "adj_close", precision = 12, scale = 4, nullable = false)
    private BigDecimal adjClose;

    @Column(nullable = false)
    private long volume;

    protected CandleEntity() {
    }

    public CandleEntity(String ticker, LocalDate tradeDate,
                         BigDecimal open, BigDecimal high, BigDecimal low,
                         BigDecimal close, BigDecimal adjClose, long volume) {
        this.ticker = ticker;
        this.tradeDate = tradeDate;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.adjClose = adjClose;
        this.volume = volume;
    }

    /** 기존 Candle record 변환. close 대신 adjClose 사용 (차트/지표 정합). */
    public Candle toCandle() {
        long epochSec = tradeDate.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        return new Candle(epochSec, open, high, low, adjClose, volume);
    }

    public String getTicker() { return ticker; }
    public LocalDate getTradeDate() { return tradeDate; }
    public BigDecimal getOpen() { return open; }
    public BigDecimal getHigh() { return high; }
    public BigDecimal getLow() { return low; }
    public BigDecimal getClose() { return close; }
    public BigDecimal getAdjClose() { return adjClose; }
    public long getVolume() { return volume; }
}
