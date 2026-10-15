CREATE TABLE extract_result (
                                id              INTEGER         PRIMARY KEY AUTOINCREMENT,
                                company         VARCHAR(255)    NOT NULL,
                                period          VARCHAR(50)     NOT NULL,
                                extracted_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                document        VARCHAR(255),
                                extract_status  VARCHAR(20)     NOT NULL
);