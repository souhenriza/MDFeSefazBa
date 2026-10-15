ALTER TABLE extraction_batch ADD COLUMN description VARCHAR(255);
ALTER TABLE extraction_batch ADD COLUMN gen_report BOOLEAN NOT NULL DEFAULT 1;
ALTER TABLE extraction_batch ADD COLUMN report_format VARCHAR(10);
ALTER TABLE extraction_batch ADD COLUMN output_path VARCHAR(500);
