-- V20__add_parent_id_to_work_item.sql
-- Add parent_id column to work_item for threaded inbox support.
-- Enables root detection (parentId IS NULL) and efficient child queries
-- without NOT EXISTS subqueries on work_item_relation.

ALTER TABLE work_item ADD COLUMN parent_id UUID;
ALTER TABLE work_item ADD CONSTRAINT fk_work_item_parent FOREIGN KEY (parent_id) REFERENCES work_item(id) ON DELETE SET NULL;
CREATE INDEX idx_work_item_parent_id ON work_item(parent_id);
