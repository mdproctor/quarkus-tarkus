-- V4: WorkItemNote — internal operational annotations on WorkItems
--
-- Notes are distinct from:
--   audit_entry  — automatic, structured, immutable, system-generated
--   issue_tracker_comment — external, in GitHub/Jira, community-facing
--
-- A note captures the operational "why": why was this delegated, what was
-- discovered during review, what context the next assignee needs. It belongs
-- to the work unit, is visible to the assignee chain, and may contain internal
-- detail unsuitable for an external issue tracker.
--
-- Notes are editable (edited_at records last modification) and deletable.
-- They are NOT part of the cryptographic audit trail — use AuditEntry for that.

CREATE TABLE work_item_note (
    id           UUID         NOT NULL,
    work_item_id UUID         NOT NULL REFERENCES work_item(id) ON DELETE CASCADE,
    content      TEXT         NOT NULL,
    author       VARCHAR(255) NOT NULL,
    created_at   TIMESTAMP    NOT NULL,
    edited_at    TIMESTAMP,
    CONSTRAINT pk_work_item_note PRIMARY KEY (id)
);

CREATE INDEX idx_win_work_item_id ON work_item_note (work_item_id);
