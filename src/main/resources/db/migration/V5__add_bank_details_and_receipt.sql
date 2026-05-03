-- Add bank details to shops
ALTER TABLE staff_shops ADD COLUMN bank_name VARCHAR(100);
ALTER TABLE staff_shops ADD COLUMN account_number VARCHAR(50);

-- Add receipt image to appointments
ALTER TABLE appointments ADD COLUMN receipt_image_url VARCHAR(500);
