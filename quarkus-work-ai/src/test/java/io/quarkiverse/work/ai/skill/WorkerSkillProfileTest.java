package io.quarkiverse.work.ai.skill;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class WorkerSkillProfileTest {

    @Test
    void setNarrative_updatesField() {
        final var p = new WorkerSkillProfile();
        p.workerId = "alice";
        p.narrative = "legal expert, 47 NDA reviews";
        assertThat(p.workerId).isEqualTo("alice");
        assertThat(p.narrative).contains("47 NDA");
    }

    @Test
    void prePersist_setsTimestamps() {
        final var p = new WorkerSkillProfile();
        p.workerId = "bob";
        p.onPrePersist();
        assertThat(p.createdAt).isNotNull();
        assertThat(p.updatedAt).isNotNull();
    }

    @Test
    void preUpdate_updatesUpdatedAt() throws InterruptedException {
        final var p = new WorkerSkillProfile();
        p.workerId = "carol";
        p.onPrePersist();
        final Instant before = p.updatedAt;
        Thread.sleep(2);
        p.onPreUpdate();
        assertThat(p.updatedAt).isAfterOrEqualTo(before);
    }
}
