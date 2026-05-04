-- Add payment status tracking to appointments (idempotent)
ALTER TABLE appointments ADD COLUMN IF NOT EXISTS payment_status VARCHAR(30) NOT NULL DEFAULT 'UNPAID';

-- Ensure check constraint exists (drop first to be safe if it already exists from a manual run)
ALTER TABLE appointments DROP CONSTRAINT IF EXISTS appointments_payment_status_check;
ALTER TABLE appointments ADD CONSTRAINT appointments_payment_status_check 
CHECK (payment_status IN ('UNPAID', 'PARTIALLY_PAID', 'PAID', 'PENDING', 'SUCCESS', 'FAILED', 'CANCELLED', 'REFUNDED'));
