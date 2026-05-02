-- ============================================================
-- TrimLink Database Migration V1
-- Full schema for all entities
-- ============================================================

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ─── USERS ───────────────────────────────────────────────────
CREATE TABLE users (
    id              UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    username        VARCHAR(50) NOT NULL UNIQUE,
    password        VARCHAR(255) NOT NULL,
    phone_number    VARCHAR(20) UNIQUE,
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,
    email           VARCHAR(150),
    avatar_url      TEXT,
    role            VARCHAR(20)  NOT NULL CHECK (role IN ('CUSTOMER','BARBER','OWNER','ADMIN')),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    phone_verified  BOOLEAN      NOT NULL DEFAULT FALSE,
    approval_status VARCHAR(20)  NOT NULL DEFAULT 'APPROVED' CHECK (approval_status IN ('PENDING','APPROVED','REJECTED')),
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at      TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(255),
    updated_by      VARCHAR(255)
);

CREATE INDEX idx_users_phone ON users(phone_number);
CREATE INDEX idx_users_role  ON users(role);

-- ─── BARBER SHOPS ────────────────────────────────────────────
CREATE TABLE barber_shops (
    id          UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    name        VARCHAR(200) NOT NULL,
    phone       VARCHAR(20),
    address     TEXT         NOT NULL,
    city        VARCHAR(100) NOT NULL,
    latitude    DOUBLE PRECISION,
    longitude   DOUBLE PRECISION,
    description VARCHAR(500),
    logo_url    TEXT,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    deleted     BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at  TIMESTAMP,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255)
);

CREATE INDEX idx_shops_city   ON barber_shops(city);
CREATE INDEX idx_shops_active ON barber_shops(active);

-- ─── WORKING HOURS ───────────────────────────────────────────
CREATE TABLE working_hours (
    id          UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    shop_id     UUID        NOT NULL REFERENCES barber_shops(id) ON DELETE CASCADE,
    day_of_week VARCHAR(15) NOT NULL,
    open_time   TIME        NOT NULL,
    close_time  TIME        NOT NULL,
    closed      BOOLEAN     NOT NULL DEFAULT FALSE,
    deleted     BOOLEAN     NOT NULL DEFAULT FALSE,
    deleted_at  TIMESTAMP,
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP   NOT NULL DEFAULT NOW(),
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255),
    UNIQUE (shop_id, day_of_week)
);

-- ─── BARBER PROFILES ─────────────────────────────────────────
CREATE TABLE barber_profiles (
    id               UUID           PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id          UUID           NOT NULL UNIQUE REFERENCES users(id),
    shop_id          UUID           REFERENCES barber_shops(id),
    bio              VARCHAR(500),
    experience_years INTEGER,
    average_rating   NUMERIC(3,2)   NOT NULL DEFAULT 0.00,
    total_reviews    INTEGER        NOT NULL DEFAULT 0,
    is_available     BOOLEAN        NOT NULL DEFAULT TRUE,
    deleted          BOOLEAN        NOT NULL DEFAULT FALSE,
    deleted_at       TIMESTAMP,
    created_at       TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP      NOT NULL DEFAULT NOW(),
    created_by       VARCHAR(255),
    updated_by       VARCHAR(255)
);

CREATE INDEX idx_barber_shop ON barber_profiles(shop_id);

-- ─── SERVICES ────────────────────────────────────────────────
CREATE TABLE services (
    id               UUID           PRIMARY KEY DEFAULT uuid_generate_v4(),
    name             VARCHAR(150)   NOT NULL,
    description      VARCHAR(400),
    base_price       NUMERIC(10,2)  NOT NULL,
    duration_minutes INTEGER        NOT NULL,
    image_url        TEXT,
    active           BOOLEAN        NOT NULL DEFAULT TRUE,
    deleted          BOOLEAN        NOT NULL DEFAULT FALSE,
    deleted_at       TIMESTAMP,
    created_at       TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP      NOT NULL DEFAULT NOW(),
    created_by       VARCHAR(255),
    updated_by       VARCHAR(255)
);

-- ─── BARBER SERVICE ASSIGNMENTS ───────────────────────────────
CREATE TABLE barber_service_assignments (
    id                UUID          PRIMARY KEY DEFAULT uuid_generate_v4(),
    barber_profile_id UUID          NOT NULL REFERENCES barber_profiles(id) ON DELETE CASCADE,
    service_id        UUID          NOT NULL REFERENCES services(id),
    custom_price      NUMERIC(10,2),
    active            BOOLEAN       NOT NULL DEFAULT TRUE,
    deleted           BOOLEAN       NOT NULL DEFAULT FALSE,
    deleted_at        TIMESTAMP,
    created_at        TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP     NOT NULL DEFAULT NOW(),
    created_by        VARCHAR(255),
    updated_by        VARCHAR(255),
    UNIQUE (barber_profile_id, service_id)
);

-- ─── APPOINTMENTS ────────────────────────────────────────────
CREATE TABLE appointments (
    id                  UUID           PRIMARY KEY DEFAULT uuid_generate_v4(),
    customer_id         UUID           NOT NULL REFERENCES users(id),
    barber_profile_id   UUID           NOT NULL REFERENCES barber_profiles(id),
    shop_id             UUID           NOT NULL REFERENCES barber_shops(id),
    service_id          UUID           NOT NULL REFERENCES services(id),
    scheduled_start     TIMESTAMP      NOT NULL,
    scheduled_end       TIMESTAMP      NOT NULL,
    actual_start        TIMESTAMP,
    actual_end          TIMESTAMP,
    status              VARCHAR(20)    NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING','CONFIRMED','COMPLETED','CANCELLED','NO_SHOW')),
    price_charged       NUMERIC(10,2)  NOT NULL,
    notes               VARCHAR(500),
    cancellation_reason VARCHAR(300),
    deleted             BOOLEAN        NOT NULL DEFAULT FALSE,
    deleted_at          TIMESTAMP,
    created_at          TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP      NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(255),
    updated_by          VARCHAR(255)
);

CREATE INDEX idx_appt_barber_start ON appointments(barber_profile_id, scheduled_start);
CREATE INDEX idx_appt_customer     ON appointments(customer_id);
CREATE INDEX idx_appt_status       ON appointments(status);
CREATE INDEX idx_appt_shop_date    ON appointments(shop_id, scheduled_start);

-- ─── QUEUE ENTRIES ───────────────────────────────────────────
CREATE TABLE queue_entries (
    id                UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    customer_id       UUID        NOT NULL REFERENCES users(id),
    barber_profile_id UUID        NOT NULL REFERENCES barber_profiles(id),
    shop_id           UUID        NOT NULL REFERENCES barber_shops(id),
    service_id        UUID        NOT NULL REFERENCES services(id),
    joined_at         TIMESTAMP   NOT NULL,
    client_timestamp  TIMESTAMP,
    status            VARCHAR(20) NOT NULL DEFAULT 'WAITING'
                      CHECK (status IN ('WAITING','CALLED','IN_SERVICE','COMPLETED','CANCELLED','SKIPPED')),
    called_at            TIMESTAMP,
    service_started_at   TIMESTAMP,
    service_ended_at     TIMESTAMP,
    notes                VARCHAR(300),
    deleted              BOOLEAN   NOT NULL DEFAULT FALSE,
    deleted_at           TIMESTAMP,
    created_at           TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by           VARCHAR(255),
    updated_by           VARCHAR(255)
);

CREATE INDEX idx_queue_barber_status ON queue_entries(barber_profile_id, status);
CREATE INDEX idx_queue_shop_status   ON queue_entries(shop_id, status);
CREATE INDEX idx_queue_joined_at     ON queue_entries(joined_at);

-- ─── PAYMENTS ────────────────────────────────────────────────
CREATE TABLE payments (
    id               UUID           PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id          UUID           NOT NULL REFERENCES users(id),
    reference_id     UUID           NOT NULL,
    reference_type   VARCHAR(20)    NOT NULL CHECK (reference_type IN ('APPOINTMENT','QUEUE_ENTRY')),
    provider         VARCHAR(20)    NOT NULL CHECK (provider IN ('CHAPA','TELEBIRR')),
    status           VARCHAR(20)    NOT NULL DEFAULT 'PENDING'
                     CHECK (status IN ('PENDING','SUCCESS','FAILED','CANCELLED','REFUNDED')),
    amount           NUMERIC(10,2)  NOT NULL,
    currency         VARCHAR(5)     NOT NULL DEFAULT 'ETB',
    tx_ref           VARCHAR(100)   NOT NULL UNIQUE,
    checkout_url     VARCHAR(500),
    provider_tx_id   VARCHAR(200),
    webhook_payload  TEXT,
    paid_at          TIMESTAMP,
    failed_reason    VARCHAR(500),
    deleted          BOOLEAN        NOT NULL DEFAULT FALSE,
    deleted_at       TIMESTAMP,
    created_at       TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP      NOT NULL DEFAULT NOW(),
    created_by       VARCHAR(255),
    updated_by       VARCHAR(255)
);

CREATE INDEX idx_payment_tx_ref    ON payments(tx_ref);
CREATE INDEX idx_payment_user      ON payments(user_id);
CREATE INDEX idx_payment_status    ON payments(status);
CREATE INDEX idx_payment_reference ON payments(reference_id);

-- ─── REVIEWS ─────────────────────────────────────────────────
CREATE TABLE reviews (
    id                UUID          PRIMARY KEY DEFAULT uuid_generate_v4(),
    appointment_id    UUID          NOT NULL UNIQUE REFERENCES appointments(id),
    reviewer_id       UUID          NOT NULL REFERENCES users(id),
    barber_profile_id UUID          NOT NULL REFERENCES barber_profiles(id),
    rating            NUMERIC(2,1)  NOT NULL CHECK (rating >= 1.0 AND rating <= 5.0),
    comment           VARCHAR(500),
    deleted           BOOLEAN       NOT NULL DEFAULT FALSE,
    deleted_at        TIMESTAMP,
    created_at        TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP     NOT NULL DEFAULT NOW(),
    created_by        VARCHAR(255),
    updated_by        VARCHAR(255)
);

CREATE INDEX idx_reviews_barber ON reviews(barber_profile_id);

-- ─── SEED DEFAULT ADMIN USER ──────────────────────────────────
-- Password is 'admin123' (BCrypt hashed)
INSERT INTO users (id, username, password, phone_number, first_name, last_name, role, active, phone_verified, approval_status)
VALUES (uuid_generate_v4(), 'admin', '$2a$12$6.Z5T.vYw7zN3rR9Xm0X.O6tE7f7eK6Z9W6Xm0X.O6tE7f7eK6Z9', '+251911000000', 'TrimLink', 'Admin', 'ADMIN', TRUE, TRUE, 'APPROVED')
ON CONFLICT (username) DO NOTHING;
