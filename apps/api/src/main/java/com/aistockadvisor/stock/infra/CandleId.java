package com.aistockadvisor.stock.infra;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/**
 * candles 테이블 복합 PK: (ticker, tradeDate).
 */
public class CandleId implements Serializable {

    private String ticker;
    private LocalDate tradeDate;

    public CandleId() {
    }

    public CandleId(String ticker, LocalDate tradeDate) {
        this.ticker = ticker;
        this.tradeDate = tradeDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CandleId that)) return false;
        return Objects.equals(ticker, that.ticker) && Objects.equals(tradeDate, that.tradeDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ticker, tradeDate);
    }
}
