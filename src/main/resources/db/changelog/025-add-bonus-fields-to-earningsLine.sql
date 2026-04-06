ALTER TABLE earnings_line
    ADD COLUMN earnings_type VARCHAR(32),
    ADD COLUMN taxable_amount NUMERIC(38, 2),
    ADD COLUMN non_taxable_amount NUMERIC(38, 2);

UPDATE earnings_line
SET earnings_type = 'FIXED'
WHERE earnings_type IS NULL;

UPDATE earnings_line
SET taxable_amount = amount
WHERE taxable_amount IS NULL;

UPDATE earnings_line
SET non_taxable_amount = 0
WHERE non_taxable_amount IS NULL;

ALTER TABLE earnings_line
    ALTER COLUMN earnings_type SET DEFAULT 'FIXED',
    ALTER COLUMN earnings_type SET NOT NULL,
    ALTER COLUMN taxable_amount SET DEFAULT 0,
    ALTER COLUMN taxable_amount SET NOT NULL,
    ALTER COLUMN non_taxable_amount SET DEFAULT 0,
    ALTER COLUMN non_taxable_amount SET NOT NULL;
