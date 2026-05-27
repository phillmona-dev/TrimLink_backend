-- Add missing columns only if they don't already exist
ALTER TABLE audit_logs 
    ADD COLUMN IF NOT EXISTS browser VARCHAR(255),
    ADD COLUMN IF NOT EXISTS os VARCHAR(255),
    ADD COLUMN IF NOT EXISTS device VARCHAR(255),
    ADD COLUMN IF NOT EXISTS request_url VARCHAR(512),
    ADD COLUMN IF NOT EXISTS request_method VARCHAR(10);

-- Update user_agent length
ALTER TABLE audit_logs 
    ALTER COLUMN user_agent TYPE VARCHAR(512);
