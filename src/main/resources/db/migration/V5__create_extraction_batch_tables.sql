CREATE TABLE extraction_batch (
                                  id              INTEGER         PRIMARY KEY AUTOINCREMENT,
                                  batch_type      VARCHAR(50)     NOT NULL,
                                  period_label    VARCHAR(20)     NOT NULL,
                                  start_date      DATE            NOT NULL,
                                  end_date        DATE            NOT NULL,
                                  status          VARCHAR(30)     NOT NULL,
                                  started_at      TIMESTAMP       NOT NULL,
                                  finished_at     TIMESTAMP
);

CREATE TABLE extraction_batch_item (
                                       id              INTEGER         PRIMARY KEY AUTOINCREMENT,
                                       batch_id        INTEGER         NOT NULL REFERENCES extraction_batch (id),
                                       company_id      INTEGER         NOT NULL,
                                       company_name    VARCHAR(255)    NOT NULL,
                                       status          VARCHAR(20)     NOT NULL,
                                       attempts        INTEGER         NOT NULL DEFAULT 0,
                                       error_message   TEXT,
                                       report_paths    TEXT,
                                       started_at      TIMESTAMP,
                                       finished_at     TIMESTAMP
);

CREATE INDEX idx_extraction_batch_item_batch ON extraction_batch_item (batch_id, status);
