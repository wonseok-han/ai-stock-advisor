package com.nowini.sec.infra;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

public interface SecFilingSummaryRepository extends JpaRepository<SecFilingSummaryEntity, Long> {

    List<SecFilingSummaryEntity> findByAccessionNumberIn(List<String> accessionNumbers);

    @Modifying
    @Transactional
    @Query(value = "INSERT INTO sec_filing_summaries "
            + "(ticker, accession_number, form, event_category, filed_at, "
            + "document_url, content_summary, summary_ko, sentiment, created_at) "
            + "VALUES (:#{#e.ticker}, :#{#e.accessionNumber}, :#{#e.form}, "
            + ":#{#e.eventCategory}, :#{#e.filedAt}, :#{#e.documentUrl}, "
            + ":#{#e.contentSummary}, :#{#e.summaryKo}, :#{#e.sentiment}, NOW()) "
            + "ON CONFLICT (accession_number) DO NOTHING", nativeQuery = true)
    void insertIgnoreDuplicate(@org.springframework.data.repository.query.Param("e") SecFilingSummaryEntity e);

    @Modifying
    @Transactional
    @Query(value = "UPDATE sec_filing_summaries "
            + "SET content_summary = :content, summary_ko = :summary, "
            + "sentiment = :sentiment WHERE accession_number = :accNum",
            nativeQuery = true)
    void updateSummary(@org.springframework.data.repository.query.Param("accNum") String accessionNumber,
                       @org.springframework.data.repository.query.Param("content") String contentSummary,
                       @org.springframework.data.repository.query.Param("summary") String summaryKo,
                       @org.springframework.data.repository.query.Param("sentiment") String sentiment);
}
