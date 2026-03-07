CREATE TABLE refresh_token (
    refresh_id BIGSERIAL PRIMARY KEY,
    refresh_token VARCHAR(255) NOT NULL UNIQUE,
    username VARCHAR(255) NOT NULL,
    expiry_date TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_refresh_user FOREIGN KEY (username) REFERENCES user_account(username)
);