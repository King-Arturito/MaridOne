ALTER TABLE user_account
ADD COLUMN password_reset_secret VARCHAR(255),
ADD COLUMN password_reset_expires_at TIMESTAMP WITH TIME ZONE;
