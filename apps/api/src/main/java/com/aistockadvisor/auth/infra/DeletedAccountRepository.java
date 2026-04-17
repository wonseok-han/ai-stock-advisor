package com.aistockadvisor.auth.infra;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DeletedAccountRepository extends JpaRepository<DeletedAccountEntity, UUID> {
    boolean existsByUserId(UUID userId);
}
