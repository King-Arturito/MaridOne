ALTER TABLE employee
    ADD COLUMN gender VARCHAR(255);

ALTER TABLE employee
    ADD CONSTRAINT employee_gender_check
        CHECK (gender IN ('MALE', 'FEMALE', 'NON_BINARY', 'PREFER_NOT_TO_SAY'));
