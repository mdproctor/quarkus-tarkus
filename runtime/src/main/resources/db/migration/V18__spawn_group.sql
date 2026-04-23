CREATE TABLE work_item_spawn_group (
    id              UUID        NOT NULL,
    parent_id       UUID        NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    created_at      TIMESTAMP   NOT NULL,
    CONSTRAINT pk_work_item_spawn_group PRIMARY KEY (id),
    CONSTRAINT uq_spawn_group_idempotency UNIQUE (parent_id, idempotency_key)
);
