CREATE TABLE escalation_summary (
    id            UUID         NOT NULL,
    work_item_id  UUID         NOT NULL,
    event_type    VARCHAR(50)  NOT NULL,
    summary       TEXT,
    generated_at  TIMESTAMP    NOT NULL,
    CONSTRAINT pk_escalation_summary PRIMARY KEY (id)
);

CREATE INDEX idx_escalation_summary_work_item_id ON escalation_summary (work_item_id);
