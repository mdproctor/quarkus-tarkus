-- Rename WorkItemPriority stored values to align with Linear vocabulary.
-- NORMAL -> MEDIUM (scheduling priority, standard term)
-- CRITICAL -> URGENT (top scheduling priority, not ITSM severity)
UPDATE work_item SET priority = 'MEDIUM' WHERE priority = 'NORMAL';
UPDATE work_item SET priority = 'URGENT' WHERE priority = 'CRITICAL';
ALTER TABLE work_item ALTER COLUMN priority SET DEFAULT 'MEDIUM';
