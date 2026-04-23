-- quarkus-work-issue-tracker V3000: WorkItem → external issue links
--
-- work_item_issue_link: one row per (WorkItem, external issue) pair.
--   tracker_type  — "github", "jira", "linear", or any custom type
--   external_ref  — tracker-specific reference; for GitHub: "owner/repo#42"
--   title         — cached display title from the remote issue at link time
--   url           — direct URL to the issue in its tracker
--   status        — last-known status: "open", "closed", "unknown"
--   linked_at     — when the link was created
--   linked_by     — actor who created the link (user or system)
--
-- status is refreshed by PUT /workitems/{id}/issues/sync. It is intentionally
-- not kept continuously in sync — the issue tracker is the source of truth for
-- its own state. status here is a cached hint for display purposes only.

CREATE TABLE work_item_issue_link (
    id            UUID         NOT NULL,
    -- Logical reference to a WorkItem. No FK enforced: WorkItems may live in
    -- any backend (JPA, MongoDB, Redis). Cascade deletion is handled in application
    -- code via WorkItemLifecycleEvent (CANCELLED/COMPLETED). See IssueLinkService.
    work_item_id  UUID         NOT NULL,
    tracker_type  VARCHAR(50)  NOT NULL,
    external_ref  VARCHAR(500) NOT NULL,
    title         VARCHAR(500),
    url           VARCHAR(2000),
    status        VARCHAR(50)  NOT NULL DEFAULT 'unknown',
    linked_at     TIMESTAMP    NOT NULL,
    linked_by     VARCHAR(255) NOT NULL,
    CONSTRAINT pk_work_item_issue_link PRIMARY KEY (id),
    CONSTRAINT uq_work_item_issue_link UNIQUE (work_item_id, tracker_type, external_ref)
);

CREATE INDEX idx_wiil_work_item_id ON work_item_issue_link (work_item_id);
CREATE INDEX idx_wiil_tracker_ref ON work_item_issue_link (tracker_type, external_ref);
