package com.aistockadvisor.bookmark.infra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "bookmarks")
public class BookmarkEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 16)
    private String ticker;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    protected BookmarkEntity() {
    }

    public BookmarkEntity(UUID userId, String ticker) {
        this.userId = userId;
        this.ticker = ticker.toUpperCase();
        this.createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getTicker() { return ticker; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
