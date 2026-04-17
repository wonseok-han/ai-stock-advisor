package com.aistockadvisor.auth.infra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "deleted_accounts")
public class DeletedAccountEntity {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(length = 255)
    private String email;

    @Column(name = "deleted_at", nullable = false)
    private OffsetDateTime deletedAt;

    @Column(length = 100)
    private String reason;

    protected DeletedAccountEntity() {}

    public DeletedAccountEntity(UUID userId, String email, String reason) {
        this.userId = userId;
        this.email = email;
        this.reason = reason;
        this.deletedAt = OffsetDateTime.now();
    }

    public UUID getUserId() { return userId; }
    public OffsetDateTime getDeletedAt() { return deletedAt; }
}
