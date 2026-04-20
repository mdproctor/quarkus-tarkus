-- V8: WorkItemLink — structured references to external resources
--
-- WorkItemLink stores references to external resources: design specs,
-- policy documents, regulatory texts, S3/GCS file references, evidence
-- artifacts. WorkItems stores the URL reference only — file content
-- lives in external storage.
--
-- This is distinct from work_item_issue_link (tracked tickets with sync/
-- auto-close semantics). WorkItemLink is for *any* external resource.
--
-- relation_type: pluggable string, not an enum. Well-known values:
--   "reference"        — general reference
--   "design-spec"      — architecture/design document
--   "policy"           — regulatory or governance document
--   "evidence"         — supporting evidence artifact
--   "source-document"  — originating document/request
--   "attachment"       — external file (S3/GCS/MinIO/SharePoint)
-- Custom types are accepted without schema change.

CREATE TABLE work_item_link (
    id              UUID          NOT NULL,
    work_item_id    UUID          NOT NULL REFERENCES work_item(id) ON DELETE CASCADE,
    url             VARCHAR(2000) NOT NULL,
    title           VARCHAR(500),
    relation_type   VARCHAR(100)  NOT NULL,
    linked_by       VARCHAR(255)  NOT NULL,
    created_at      TIMESTAMP     NOT NULL,
    CONSTRAINT pk_work_item_link PRIMARY KEY (id)
);

CREATE INDEX idx_wlink_work_item_id ON work_item_link (work_item_id);
CREATE INDEX idx_wlink_relation_type ON work_item_link (relation_type);
