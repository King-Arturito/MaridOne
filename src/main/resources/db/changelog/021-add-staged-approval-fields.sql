ALTER TABLE leave_request
    ADD COLUMN manager_approver VARCHAR(255) NULL,
    ADD COLUMN manager_approver_reason VARCHAR(255) NULL,
    ADD COLUMN manager_approved_at TIMESTAMP NULL,
    ADD COLUMN suggested_paid BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN paid_override_reason VARCHAR(255) NULL;

UPDATE leave_request
SET suggested_paid = is_paid
WHERE suggested_paid IS DISTINCT FROM is_paid;

ALTER TABLE overtime_request
    ADD COLUMN manager_approver VARCHAR(255) NULL,
    ADD COLUMN manager_approve_reason VARCHAR(255) NULL,
    ADD COLUMN manager_approved_at TIMESTAMP NULL,
    ADD COLUMN is_paid BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN suggested_paid BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN paid_override_reason VARCHAR(255) NULL;
