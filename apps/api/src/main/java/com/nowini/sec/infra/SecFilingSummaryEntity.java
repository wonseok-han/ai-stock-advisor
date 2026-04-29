package com.nowini.sec.infra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "sec_filing_summaries")
public class SecFilingSummaryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String ticker;

    @Column(nullable = false, length = 30, unique = true)
    private String accessionNumber;

    @Column(nullable = false, length = 20)
    private String form;

    @Column(nullable = false, length = 50)
    private String eventCategory;

    @Column(nullable = false)
    private LocalDate filedAt;

    @Column(length = 500)
    private String documentUrl;

    @Column(columnDefinition = "TEXT")
    private String contentSummary;

    @Column(columnDefinition = "TEXT")
    private String summaryKo;

    @Column(length = 10)
    private String sentiment;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    protected SecFilingSummaryEntity() {}

    public SecFilingSummaryEntity(String ticker, String accessionNumber, String form,
                                  String eventCategory, LocalDate filedAt, String documentUrl,
                                  String contentSummary, String summaryKo, String sentiment) {
        this.ticker = ticker.toUpperCase();
        this.accessionNumber = accessionNumber;
        this.form = form;
        this.eventCategory = eventCategory;
        this.filedAt = filedAt;
        this.documentUrl = documentUrl;
        this.contentSummary = contentSummary;
        this.summaryKo = summaryKo;
        this.sentiment = sentiment;
        this.createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public String getTicker() { return ticker; }
    public String getAccessionNumber() { return accessionNumber; }
    public String getForm() { return form; }
    public String getEventCategory() { return eventCategory; }
    public LocalDate getFiledAt() { return filedAt; }
    public String getDocumentUrl() { return documentUrl; }
    public String getContentSummary() { return contentSummary; }
    public String getSummaryKo() { return summaryKo; }
    public String getSentiment() { return sentiment; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
