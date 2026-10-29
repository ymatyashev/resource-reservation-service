-- V1: Initial schema for Resource Reservation Service

CREATE TABLE resources (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE reservations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    resource_id UUID NOT NULL REFERENCES resources(id),
    customer_id VARCHAR(255) NOT NULL,
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'CONFIRMED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_reservations_time CHECK (start_time < end_time),
    CONSTRAINT chk_reservations_status CHECK (status IN ('CONFIRMED', 'CANCELLED'))
);

CREATE TABLE idempotency_keys (
    key VARCHAR(255) PRIMARY KEY,
    reservation_id UUID NOT NULL REFERENCES reservations(id),
    request_hash VARCHAR(64) NOT NULL
);

CREATE INDEX idx_reservations_customer ON reservations(customer_id);
CREATE INDEX idx_reservations_resource_time ON reservations(resource_id, start_time, end_time);
