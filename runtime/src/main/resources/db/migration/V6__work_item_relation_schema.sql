-- V6: WorkItemRelation — directed relation graph between WorkItems
--
-- relation_type is a plain VARCHAR, not an enum — intentionally pluggable.
-- Well-known types are constants in WorkItemRelationType; custom types
-- (TRIGGERED_BY, APPROVED_BY, ESCALATED_FROM, etc.) are accepted without
-- any schema change. Consumers define their own vocabulary.
--
-- Directionality: source → target.
--   "source PART_OF target" means source is a child of target.
--   "source BLOCKS target" means source blocks target.
--
-- Cycle prevention for PART_OF is enforced in application code (not DB),
-- because the check requires graph traversal that a CHECK constraint cannot do.
--
-- UNIQUE (source_id, target_id, relation_type) prevents duplicate edges.

CREATE TABLE work_item_relation (
    id              UUID         NOT NULL,
    source_id       UUID         NOT NULL REFERENCES work_item(id) ON DELETE CASCADE,
    target_id       UUID         NOT NULL REFERENCES work_item(id) ON DELETE CASCADE,
    relation_type   VARCHAR(100) NOT NULL,
    created_by      VARCHAR(255) NOT NULL,
    created_at      TIMESTAMP    NOT NULL,
    CONSTRAINT pk_work_item_relation PRIMARY KEY (id),
    CONSTRAINT uq_work_item_relation UNIQUE (source_id, target_id, relation_type)
);

CREATE INDEX idx_wir_source_id ON work_item_relation (source_id);
CREATE INDEX idx_wir_target_id ON work_item_relation (target_id);
CREATE INDEX idx_wir_relation_type ON work_item_relation (relation_type);
