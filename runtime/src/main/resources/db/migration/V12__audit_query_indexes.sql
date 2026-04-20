-- V12: Cross-WorkItem audit query indexes (Issue #109, Epic #99)
--
-- The cross-WorkItem audit query GET /audit filters primarily by actor,
-- occurred_at, and event. These indexes support:
--
--   GET /audit?actorId=alice           → idx_audit_actor
--   GET /audit?from=...&to=...         → idx_audit_occurred_at (already in V1)
--   GET /audit?event=COMPLETED         → idx_audit_event
--   GET /audit?actorId=alice&from=...  → idx_audit_actor_occurred_at (composite)
--
-- The idx_audit_entry_occurred_at index in V1 is retained and complemented.

CREATE INDEX idx_audit_actor ON audit_entry (actor);
CREATE INDEX idx_audit_event ON audit_entry (event);
CREATE INDEX idx_audit_actor_occurred_at ON audit_entry (actor, occurred_at);
