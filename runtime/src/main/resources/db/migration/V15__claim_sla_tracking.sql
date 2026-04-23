-- Claim SLA tracking fields for ClaimSlaPolicy integration.
-- accumulated_unclaimed_seconds — total pool-phase time used across all claim cycles.
-- last_returned_to_pool_at     — start of the current pool phase; null while held.
ALTER TABLE work_item ADD COLUMN accumulated_unclaimed_seconds BIGINT NOT NULL DEFAULT 0;
ALTER TABLE work_item ADD COLUMN last_returned_to_pool_at      TIMESTAMP;
