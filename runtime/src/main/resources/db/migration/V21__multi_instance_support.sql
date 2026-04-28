-- V21__multi_instance_support.sql
-- Add multi-instance tracking columns to work_item_spawn_group and work_item_template.
-- Supports M-of-N parallel completion policies with OCC version tracking.

-- WorkItemSpawnGroup: OCC + policy tracking
ALTER TABLE work_item_spawn_group ADD COLUMN instance_count       INTEGER;
ALTER TABLE work_item_spawn_group ADD COLUMN required_count       INTEGER;
ALTER TABLE work_item_spawn_group ADD COLUMN on_threshold_reached VARCHAR(10);
ALTER TABLE work_item_spawn_group ADD COLUMN allow_same_assignee  BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE work_item_spawn_group ADD COLUMN parent_role          VARCHAR(15);
ALTER TABLE work_item_spawn_group ADD COLUMN completed_count      INTEGER NOT NULL DEFAULT 0;
ALTER TABLE work_item_spawn_group ADD COLUMN rejected_count       INTEGER NOT NULL DEFAULT 0;
ALTER TABLE work_item_spawn_group ADD COLUMN policy_triggered     BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE work_item_spawn_group ADD COLUMN version              BIGINT NOT NULL DEFAULT 0;

-- WorkItemTemplate: multi-instance config
ALTER TABLE work_item_template ADD COLUMN instance_count          INTEGER;
ALTER TABLE work_item_template ADD COLUMN required_count          INTEGER;
ALTER TABLE work_item_template ADD COLUMN parent_role             VARCHAR(15);
ALTER TABLE work_item_template ADD COLUMN assignment_strategy     VARCHAR(255);
ALTER TABLE work_item_template ADD COLUMN on_threshold_reached    VARCHAR(10);
ALTER TABLE work_item_template ADD COLUMN allow_same_assignee     BOOLEAN;
