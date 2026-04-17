package com.aistockadvisor.bookmark.infra;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookmarkRepository extends JpaRepository<BookmarkEntity, Long> {

    List<BookmarkEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<BookmarkEntity> findByUserIdAndTicker(UUID userId, String ticker);

    boolean existsByUserIdAndTicker(UUID userId, String ticker);

    void deleteByUserIdAndTicker(UUID userId, String ticker);
}
