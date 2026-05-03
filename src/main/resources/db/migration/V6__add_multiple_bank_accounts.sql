-- Create shop_bank_accounts table
CREATE TABLE shop_bank_accounts (
    id              UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    shop_id         UUID         NOT NULL REFERENCES barber_shops(id) ON DELETE CASCADE,
    bank_name       VARCHAR(100) NOT NULL,
    account_number  VARCHAR(50)  NOT NULL,
    account_holder  VARCHAR(200),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at      TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(255),
    updated_by      VARCHAR(255)
);

CREATE INDEX idx_shop_bank_accounts_shop ON shop_bank_accounts(shop_id);

-- Migration: Copy existing bank details from barber_shops to the new table
INSERT INTO shop_bank_accounts (shop_id, bank_name, account_number, active)
SELECT id, bank_name, account_number, TRUE
FROM barber_shops
WHERE bank_name IS NOT NULL AND account_number IS NOT NULL;

-- Optional: Remove old columns from barber_shops (wait until verification)
-- ALTER TABLE barber_shops DROP COLUMN bank_name;
-- ALTER TABLE barber_shops DROP COLUMN account_number;
