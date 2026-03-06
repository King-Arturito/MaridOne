CREATE TABLE user_account (
    username        VARCHAR(30)     PRIMARY KEY,
    password_hash   VARCHAR(255)    NOT NULL,
    account_status  VARCHAR(255)    NOT NULL,
    emp_id          BIGINT,

    CONSTRAINT user_account_account_status_check CHECK (account_status IN ('INACTIVE', 'ACTIVE')),
    CONSTRAINT fk_user_account_employee FOREIGN KEY (emp_id) REFERENCES employee (emp_id)
);
