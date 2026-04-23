-- V16: FilterRule — dynamic (DB-persisted) filter rules (Issue #113, Epic #100, moved from quarkus-work-core by #133)
--
-- Dynamic rules complement CDI-produced permanent filters. Operators create,
-- enable/disable, and delete rules at runtime without redeployment.
--
-- events: comma-separated FilterEvent names (ADD, UPDATE, REMOVE)
-- actions_json: JSON array of {type, params} ActionDescriptors

CREATE TABLE filter_rule (
    id           UUID          NOT NULL,
    name         VARCHAR(255)  NOT NULL,
    description  VARCHAR(500),
    enabled      BOOLEAN       NOT NULL DEFAULT TRUE,
    condition    TEXT          NOT NULL,
    events       VARCHAR(50)   NOT NULL DEFAULT 'ADD,UPDATE,REMOVE',
    actions_json TEXT          NOT NULL DEFAULT '[]',
    created_at   TIMESTAMP     NOT NULL,
    CONSTRAINT pk_filter_rule PRIMARY KEY (id)
);

CREATE INDEX idx_filter_rule_enabled ON filter_rule (enabled);
