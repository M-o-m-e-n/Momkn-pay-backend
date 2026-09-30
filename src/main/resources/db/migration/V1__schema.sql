-- Momkn Pay schema (docs/LLD.md §4.1). Money is BIGINT piastres; timestamps are TIMESTAMPTZ (UTC).

-- ============ users ============
CREATE TABLE users (
    id            VARCHAR(32)  PRIMARY KEY,
    full_name     VARCHAR(100) NOT NULL,
    mobile        VARCHAR(11)  NOT NULL UNIQUE
                  CHECK (mobile ~ '^01[0125][0-9]{8}$'),
    email         VARCHAR(254) NOT NULL,
    pin_hash      VARCHAR(72)  NOT NULL,            -- bcrypt, cost 12
    member_since  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_users_email ON users (lower(email));

-- ============ services ============
CREATE TABLE services (
    id             VARCHAR(64)  PRIMARY KEY,
    name_en        VARCHAR(100) NOT NULL,
    name_ar        VARCHAR(100) NOT NULL,
    category       VARCHAR(16)  NOT NULL
                   CHECK (category IN ('electricity','water','gas','internet','mobile','landline')),
    icon_url       VARCHAR(255) NOT NULL,
    input_label    VARCHAR(64)  NOT NULL,
    input_pattern  VARCHAR(128) NOT NULL,
    min_amount     BIGINT       NOT NULL CHECK (min_amount >= 0),
    max_amount     BIGINT       NOT NULL CHECK (max_amount >= min_amount),
    is_active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at     TIMESTAMPTZ  NULL                -- soft delete, drives deletedIds
);
CREATE INDEX ix_services_updated_at ON services (updated_at);
CREATE INDEX ix_services_deleted_at ON services (deleted_at) WHERE deleted_at IS NOT NULL;

-- keep updated_at correct even for manual SQL edits (delta sync depends on it)
CREATE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at := date_trunc('second', now());
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_services_updated_at BEFORE UPDATE ON services
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ============ sessions ============
CREATE TABLE sessions (
    id           VARCHAR(40) PRIMARY KEY,           -- ses_<32 hex>
    user_id      VARCHAR(32) NOT NULL REFERENCES users (id),
    wrapped_key  BYTEA       NOT NULL,              -- iv(12) || AES-GCM(master, key) || tag(16)
    created_at   TIMESTAMPTZ NOT NULL,
    expires_at   TIMESTAMPTZ NOT NULL,
    revoked_at   TIMESTAMPTZ NULL
);
CREATE INDEX ix_sessions_user ON sessions (user_id);
CREATE INDEX ix_sessions_expires ON sessions (expires_at);

-- ============ used_nonces ============
CREATE TABLE used_nonces (
    nonce       CHAR(32)    PRIMARY KEY,            -- 16 bytes hex, globally single-use
    session_id  VARCHAR(40) NOT NULL REFERENCES sessions (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_used_nonces_created ON used_nonces (created_at);

-- ============ inquiries ============
CREATE TABLE inquiries (
    id                   VARCHAR(32)  PRIMARY KEY,  -- inq_<16 hex>
    user_id              VARCHAR(32)  NOT NULL REFERENCES users (id),
    service_id           VARCHAR(64)  NOT NULL REFERENCES services (id),
    session_id           VARCHAR(40)  NULL REFERENCES sessions (id) ON DELETE SET NULL,
    subscriber_number    VARCHAR(32)  NOT NULL,
    customer_name        VARCHAR(100) NOT NULL,
    bill_month           CHAR(7)      NOT NULL,     -- YYYY-MM
    amount_due           BIGINT       NOT NULL CHECK (amount_due > 0),
    service_fee          BIGINT       NOT NULL CHECK (service_fee >= 0),
    vat                  BIGINT       NOT NULL CHECK (vat >= 0),
    total                BIGINT       NOT NULL,
    rule                 VARCHAR(16)  NOT NULL
                         CHECK (rule IN ('NORMAL','LARGE','DECLINE','PENDING')),
    status               VARCHAR(16)  NOT NULL DEFAULT 'OPEN'
                         CHECK (status IN ('OPEN','CONFIRMED','INVALIDATED')),
    failed_pin_attempts  SMALLINT     NOT NULL DEFAULT 0
                         CHECK (failed_pin_attempts BETWEEN 0 AND 3),
    created_at           TIMESTAMPTZ  NOT NULL,
    expires_at           TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_inquiry_total CHECK (total = amount_due + service_fee + vat)
);
CREATE INDEX ix_inquiries_user ON inquiries (user_id, created_at DESC);

-- ============ transactions ============
CREATE SEQUENCE transaction_seq START WITH 5500;

CREATE TABLE transactions (
    id                 VARCHAR(32)  PRIMARY KEY,    -- txn_<seq>
    seq                BIGINT       NOT NULL UNIQUE,
    user_id            VARCHAR(32)  NOT NULL REFERENCES users (id),
    inquiry_id         VARCHAR(32)  NOT NULL REFERENCES inquiries (id),
    service_id         VARCHAR(64)  NOT NULL REFERENCES services (id),
    idempotency_key    UUID         NOT NULL,
    status             VARCHAR(16)  NOT NULL CHECK (status IN ('SUCCESS','FAILED','PENDING')),
    failure_code       VARCHAR(32)  NULL,
    reference          VARCHAR(32)  NOT NULL UNIQUE, -- MP-YYYYMMDD-NNNN
    subscriber_number  VARCHAR(32)  NOT NULL,
    customer_name      VARCHAR(100) NOT NULL,
    bill_month         CHAR(7)      NOT NULL,
    amount_due         BIGINT       NOT NULL,
    service_fee        BIGINT       NOT NULL,
    vat                BIGINT       NOT NULL,
    total              BIGINT       NOT NULL,
    created_at         TIMESTAMPTZ  NOT NULL,
    paid_at            TIMESTAMPTZ  NULL,
    pending_until      TIMESTAMPTZ  NULL,
    CONSTRAINT ux_txn_idempotency UNIQUE (user_id, idempotency_key),
    CONSTRAINT ck_txn_total   CHECK (total = amount_due + service_fee + vat),
    CONSTRAINT ck_txn_failure CHECK ((status = 'FAILED') = (failure_code IS NOT NULL)),
    CONSTRAINT ck_txn_paid    CHECK (status <> 'SUCCESS' OR paid_at IS NOT NULL),
    CONSTRAINT ck_txn_pending CHECK (status <> 'PENDING' OR pending_until IS NOT NULL)
);
-- at most one settled (or settling) transaction per inquiry
CREATE UNIQUE INDEX ux_txn_inquiry_settled ON transactions (inquiry_id)
    WHERE status IN ('SUCCESS','PENDING');
CREATE INDEX ix_txn_user_history ON transactions (user_id, created_at DESC, seq DESC);
CREATE INDEX ix_txn_pending_due  ON transactions (pending_until) WHERE status = 'PENDING';
