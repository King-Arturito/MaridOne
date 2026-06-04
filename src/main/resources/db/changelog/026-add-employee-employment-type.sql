ALTER TABLE employee
    ADD COLUMN employment_type VARCHAR(255);

UPDATE employee
SET employment_type = CASE
    WHEN employment_status = 'NEW_HIRE' THEN 'PROBATIONARY_FULL_TIME'
    WHEN employment_status = 'PART_TIME' THEN 'PART_TIME'
    ELSE 'REGULAR_FULL_TIME'
END
WHERE employment_type IS NULL;

UPDATE employee
SET employment_status = 'REGULAR'
WHERE employment_status IN ('NEW_HIRE', 'PART_TIME');

ALTER TABLE employee
    ALTER COLUMN employment_type SET NOT NULL;

ALTER TABLE employee
    ADD CONSTRAINT employee_employment_type_check
    CHECK (employment_type IN ('REGULAR_FULL_TIME', 'PROBATIONARY_FULL_TIME', 'PART_TIME', 'ON_CALL', 'CONTRACTUAL'));
