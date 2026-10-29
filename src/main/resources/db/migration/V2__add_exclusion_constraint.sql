-- V2: Add exclusion constraint to prevent overlapping confirmed reservations at the database level

CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE reservations ADD CONSTRAINT no_overlapping_confirmed_reservations
  EXCLUDE USING gist (
    resource_id WITH =,
    tstzrange(start_time, end_time, '[)') WITH &&
  ) WHERE (status = 'CONFIRMED');
