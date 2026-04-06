ALTER TABLE user_account
ADD COLUMN password_change_required BOOLEAN DEFAULT FALSE NOT NULL;
