-- Add virtual ticket number column to appointments
ALTER TABLE appointments ADD COLUMN IF NOT EXISTS ticket_number VARCHAR(20);
