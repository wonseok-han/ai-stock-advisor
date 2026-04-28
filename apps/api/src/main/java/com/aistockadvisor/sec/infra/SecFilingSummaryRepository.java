package com.aistockadvisor.sec.infra;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SecFilingSummaryRepository extends JpaRepository<SecFilingSummaryEntity, Long> {

    List<SecFilingSummaryEntity> findByAccessionNumberIn(List<String> accessionNumbers);
}
