-- V5: WorkItemTemplate — predefined WorkItem blueprints
--
-- A template pre-defines the fields for a repeatable process:
-- category, priority, candidate groups, expiry defaults, payload, labels.
-- Instantiate via POST /workitem-templates/{id}/instantiate to create a
-- fully-configured PENDING WorkItem in a single call.
--
-- label_paths: JSON array of label path strings applied as MANUAL labels
--   at instantiation time. e.g. ["intake/triage", "priority/high"]
--
-- default_payload: JSON object used as the WorkItem's payload field.
--   May contain domain-specific context pre-filled for the process.

CREATE TABLE work_item_template (
    id                    UUID         NOT NULL,
    name                  VARCHAR(255) NOT NULL,
    description           TEXT,
    category              VARCHAR(255),
    priority              VARCHAR(20),
    candidate_groups      VARCHAR(500),
    candidate_users       VARCHAR(500),
    required_capabilities VARCHAR(500),
    default_expiry_hours  INTEGER,
    default_claim_hours   INTEGER,
    default_payload       TEXT,
    label_paths           TEXT,
    created_by            VARCHAR(255) NOT NULL,
    created_at            TIMESTAMP    NOT NULL,
    CONSTRAINT pk_work_item_template PRIMARY KEY (id)
);

CREATE INDEX idx_wit_name ON work_item_template (name);
