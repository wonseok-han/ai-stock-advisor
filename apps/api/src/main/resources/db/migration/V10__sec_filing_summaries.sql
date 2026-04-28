CREATE TABLE sec_filing_summaries (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticker      VARCHAR(10)  NOT NULL,
    accession_number VARCHAR(30) NOT NULL,
    form        VARCHAR(20)  NOT NULL,
    event_category VARCHAR(50) NOT NULL,
    filed_at    DATE         NOT NULL,
    document_url VARCHAR(500),
    content_summary TEXT,
    summary_ko  TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_sec_filing_accession UNIQUE (accession_number)
);

CREATE INDEX idx_sec_filing_ticker ON sec_filing_summaries (ticker);
