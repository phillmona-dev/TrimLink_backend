-- ============================================================
-- TrimLink Database Migration V9
-- Add 'BLOCKED' status and make fields nullable for manual slot management
-- ============================================================

-- Update status constraint
ALTER TABLE appointments DROP CONSTRAINT IF EXISTS appointments_status_check;
ALTER TABLE appointments ADD CONSTRAINT appointments_status_check 
CHECK (status IN ('PENDING', 'CONFIRMED', 'COMPLETED', 'CANCELLED', 'NO_SHOW', 'REJECTED', 'RESCHEDULE_REQUESTED', 'BLOCKED', 'IN_PROGRESS'));

-- Make fields nullable for blocked slots
ALTER TABLE appointments ALTER COLUMN customer_id DROP NOT NULL;
ALTER TABLE appointments ALTER COLUMN service_id DROP NOT NULL;
ALTER TABLE appointments ALTER COLUMN price_charged DROP NOT NULL;
