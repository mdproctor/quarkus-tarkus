-- V10: Optimistic locking for WorkItemSchedule — prevents double-fire in clusters
--
-- Adds @Version column to work_item_schedule, consistent with the @Version column
-- added to work_item in V9 for atomic claim.
--
-- When two Quarkus nodes both pick up the same due schedule, each calls
-- processSchedules() which fires in REQUIRES_NEW transactions:
--
--   UPDATE work_item_schedule SET last_fired_at=?, next_fire_at=?, version=1
--   WHERE id=? AND version=0
--
-- The first node's UPDATE succeeds; the second matches zero rows →
-- OptimisticLockException → REQUIRES_NEW rolls back. Exactly one WorkItem
-- is created per schedule per interval.

ALTER TABLE work_item_schedule ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
