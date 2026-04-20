-- V11: WorkItemFormSchema — JSON Schema definitions for WorkItem payload and resolution
--
-- A WorkItemFormSchema defines the expected shape of a WorkItem's payload and/or
-- resolution, keyed optionally by category. Category is nullable — a schema with
-- no category acts as a catch-all definition not tied to a specific work type.
--
-- Motivation: without form schemas, every UI developer must guess the payload shape.
-- With them, a UI can GET the schema for a category and auto-generate a validated form
-- (ref: Epic #98).
--
-- Storage: payloadSchema and resolutionSchema are stored as TEXT (valid JSON).
-- WorkItems stores them verbatim — JSON Schema validation happens on the client.
-- schema_version is a free-form string (e.g. "1.0", "draft-07") supplied by the caller.

CREATE TABLE work_item_form_schema (
    id                UUID          NOT NULL,
    name              VARCHAR(255)  NOT NULL,
    category          VARCHAR(255),
    payload_schema    TEXT,
    resolution_schema TEXT,
    schema_version    VARCHAR(50),
    created_by        VARCHAR(255)  NOT NULL,
    created_at        TIMESTAMP     NOT NULL,
    CONSTRAINT pk_work_item_form_schema PRIMARY KEY (id)
);

CREATE INDEX idx_wifs_category ON work_item_form_schema (category);
