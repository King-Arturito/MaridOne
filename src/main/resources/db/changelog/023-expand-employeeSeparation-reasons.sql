ALTER TABLE employee_separation DROP CONSTRAINT IF EXISTS employee_separation_reason_check;

ALTER TABLE employee_separation
ADD CONSTRAINT employee_separation_reason_check CHECK (
    reason IN (
        'TERMINATED',
        'VOLUNTARY_RESIGNATION',
        'INVOLUNTARY_SEPARATION',
        'END_OF_CONTRACT',
        'DECEASED'
    )
);
