-- Quarkus WorkItems V3: label vocabulary support
-- LabelVocabulary scopes label declarations. LabelDefinition records each declared path.
-- Compatible with H2 (dev/test) and PostgreSQL (production).

CREATE TABLE label_vocabulary (
    id          UUID            PRIMARY KEY,
    scope       VARCHAR(20)     NOT NULL,
    owner_id    VARCHAR(255),
    name        VARCHAR(255)    NOT NULL
);

-- Seed GLOBAL vocabulary (always present)
INSERT INTO label_vocabulary (id, scope, owner_id, name)
VALUES ('00000000-0000-0000-0000-000000000001', 'GLOBAL', NULL, 'Global');

CREATE TABLE label_definition (
    id              UUID            PRIMARY KEY,
    path            VARCHAR(500)    NOT NULL,
    vocabulary_id   UUID            NOT NULL REFERENCES label_vocabulary(id) ON DELETE CASCADE,
    description     VARCHAR(1000),
    created_by      VARCHAR(255)    NOT NULL,
    created_at      TIMESTAMP       NOT NULL
);

-- Seed common global labels
INSERT INTO label_definition (id, path, vocabulary_id, description, created_by, created_at) VALUES
('00000000-0000-0000-0001-000000000001', 'intake',             '00000000-0000-0000-0000-000000000001', 'Newly submitted, awaiting triage', 'system', CURRENT_TIMESTAMP),
('00000000-0000-0000-0001-000000000002', 'intake/triage',      '00000000-0000-0000-0000-000000000001', 'Actively being triaged', 'system', CURRENT_TIMESTAMP),
('00000000-0000-0000-0001-000000000003', 'priority/high',      '00000000-0000-0000-0000-000000000001', 'High priority item', 'system', CURRENT_TIMESTAMP),
('00000000-0000-0000-0001-000000000004', 'priority/critical',  '00000000-0000-0000-0000-000000000001', 'Critical priority item', 'system', CURRENT_TIMESTAMP),
('00000000-0000-0000-0001-000000000005', 'legal',              '00000000-0000-0000-0000-000000000001', 'Legal domain work', 'system', CURRENT_TIMESTAMP),
('00000000-0000-0000-0001-000000000006', 'legal/contracts',    '00000000-0000-0000-0000-000000000001', 'Contract review', 'system', CURRENT_TIMESTAMP),
('00000000-0000-0000-0001-000000000007', 'legal/compliance',   '00000000-0000-0000-0000-000000000001', 'Compliance review', 'system', CURRENT_TIMESTAMP);

CREATE INDEX idx_ld_vocabulary_id ON label_definition(vocabulary_id);
CREATE INDEX idx_ld_path ON label_definition(path);
