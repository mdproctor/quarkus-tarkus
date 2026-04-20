-- V7: WorkItemSchedule — cron-driven recurring WorkItem creation
--
-- A schedule links a WorkItemTemplate to a Quartz cron expression.
-- The background job (WorkItemScheduleService) checks every minute for
-- schedules where next_fire_at <= now, instantiates the linked template,
-- updates last_fired_at, and computes the new next_fire_at.
--
-- cron_expression: Quartz cron format (6 fields: sec min hour day month weekday)
--   Examples:
--     "0 0 9 * * ?"         — every day at 09:00
--     "0 0 9 ? * MON-FRI"   — every weekday at 09:00
--     "0 0/30 * * * ?"      — every 30 minutes
--     "0 0 0 1 * ?"         — first of every month at midnight

CREATE TABLE work_item_schedule (
    id              UUID         NOT NULL,
    name            VARCHAR(255) NOT NULL,
    template_id     UUID         NOT NULL REFERENCES work_item_template(id) ON DELETE CASCADE,
    cron_expression VARCHAR(255) NOT NULL,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by      VARCHAR(255) NOT NULL,
    created_at      TIMESTAMP    NOT NULL,
    last_fired_at   TIMESTAMP,
    next_fire_at    TIMESTAMP,
    CONSTRAINT pk_work_item_schedule PRIMARY KEY (id)
);

CREATE INDEX idx_wis_next_fire_at ON work_item_schedule (next_fire_at);
CREATE INDEX idx_wis_active ON work_item_schedule (active);
