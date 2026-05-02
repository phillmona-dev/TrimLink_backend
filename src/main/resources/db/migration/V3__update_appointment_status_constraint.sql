-- ============================================================
-- TrimLink Database Migration V3
-- Update appointment status check constraint
-- ============================================================

ALTER TABLE appointments DROP CONSTRAINT IF EXISTS appointments_status_check;

ALTER TABLE appointments ALTER COLUMN status TYPE VARCHAR(30);

ALTER TABLE appointments ADD CONSTRAINT appointments_status_check 
CHECK (status IN ('PENDING', 'CONFIRMED', 'COMPLETED', 'CANCELLED', 'NO_SHOW', 'REJECTED', 'RESCHEDULE_REQUESTED'));
