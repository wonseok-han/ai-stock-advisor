package com.aistockadvisor.stock.infra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "stock_symbol")
public class StockSymbolEntity {

    @Id
    @Column(name = "symbol", length = 20)
    private String symbol;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Column(name = "type", length = 50)
    private String type;

    @Column(name = "currency", length = 10)
    private String currency;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StockSymbolEntity() {
    }

    public StockSymbolEntity(String symbol, String description, String type, String currency, Instant updatedAt) {
        this.symbol = symbol;
        this.description = description;
        this.type = type;
        this.currency = currency;
        this.updatedAt = updatedAt;
    }

    public String getSymbol() { return symbol; }
    public String getDescription() { return description; }
    public String getType() { return type; }
    public String getCurrency() { return currency; }
    public Instant getUpdatedAt() { return updatedAt; }
}
