ALTER TABLE earnings_line
    ADD COLUMN original_amount NUMERIC(38, 2);

ALTER TABLE earnings_line
    ADD COLUMN adjusted_by VARCHAR(255);

ALTER TABLE earnings_line
    ADD COLUMN adjusted_at TIMESTAMPTZ;

ALTER TABLE deductions_line
    ADD COLUMN original_amount NUMERIC(38, 2);

ALTER TABLE deductions_line
    ADD COLUMN adjusted_by VARCHAR(255);

ALTER TABLE deductions_line
    ADD COLUMN adjusted_at TIMESTAMPTZ;
