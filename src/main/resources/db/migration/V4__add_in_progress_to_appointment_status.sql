-- ============================================================
-- TrimLink Database Migration V4
-- Add IN_PROGRESS to appointment status check constraint
-- ============================================================

ALTER TABLE appointments DROP CONSTRAINT IF EXISTS appointments_status_check;

ALTER TABLE appointments ADD CONSTRAINT appointments_status_check 
CHECK (status IN ('PENDING', 'CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'NO_SHOW', 'REJECTED', 'RESCHEDULE_REQUESTED'));
