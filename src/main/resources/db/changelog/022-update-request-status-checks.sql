ALTER TABLE leave_request
    DROP CONSTRAINT IF EXISTS leave_request_request_status_check;

ALTER TABLE leave_request
    ADD CONSTRAINT leave_request_request_status_check
        CHECK (request_status IN ('PENDING', 'PENDING_MANAGER', 'PENDING_HR', 'APPROVED', 'REJECTED', 'CANCELLED'));

ALTER TABLE overtime_request
    DROP CONSTRAINT IF EXISTS overtime_request_request_status_check;

ALTER TABLE overtime_request
    ADD CONSTRAINT overtime_request_request_status_check
        CHECK (request_status IN ('PENDING', 'PENDING_MANAGER', 'PENDING_HR', 'APPROVED', 'REJECTED', 'CANCELLED'));
