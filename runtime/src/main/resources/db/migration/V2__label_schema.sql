-- Quarkus WorkItems V2: label support
-- Each WorkItem can carry 0..n labels, each with a path, persistence type, and appliedBy actor.
-- Compatible with H2 (dev/test) and PostgreSQL (production).

CREATE TABLE work_item_label (
    work_item_id    UUID            NOT NULL,
    path            VARCHAR(500)    NOT NULL,
    persistence     VARCHAR(20)     NOT NULL,
    applied_by      VARCHAR(255)
);

CREATE INDEX idx_wil_work_item_id ON work_item_label(work_item_id);
CREATE INDEX idx_wil_path ON work_item_label(path);
