ALTER TABLE leave_request
    ADD COLUMN leave_type VARCHAR(255);

UPDATE leave_request
SET leave_type = 'VACATION_LEAVE'
WHERE leave_type IS NULL;

ALTER TABLE leave_request
    ALTER COLUMN leave_type SET NOT NULL;

ALTER TABLE leave_request
    ADD CONSTRAINT leave_request_leave_type_check CHECK (
        leave_type IN (
            'VACATION_LEAVE',
            'SICK_LEAVE',
            'MATERNITY_LEAVE',
            'PATERNITY_LEAVE',
            'SOLO_PARENT_LEAVE',
            'MAGNA_CARTA',
            'RA9262'
        )
    );
