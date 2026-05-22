-- V12: Add haircut style image support
-- barber_service_assignments can now hold a JSON array of style image URLs
-- appointments can now carry a customer's style reference image URL

ALTER TABLE barber_service_assignments
    ADD COLUMN IF NOT EXISTS style_image_urls TEXT DEFAULT '[]';

ALTER TABLE appointments
    ADD COLUMN IF NOT EXISTS style_reference_url TEXT;
