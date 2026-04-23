package io.quarkiverse.workitems.ai.skill;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

class WorkerProfileSkillProfileProviderTest {

    @Test
    void getProfile_profileExists_returnsNarrative() {
        final var profile = new WorkerSkillProfile();
        profile.workerId = "alice";
        profile.narrative = "NDA specialist, 47 reviews";

        final var provider = new WorkerProfileSkillProfileProvider(id -> Optional.of(profile));
        final var result = provider.getProfile("alice", Set.of());
        assertThat(result.narrative()).isEqualTo("NDA specialist, 47 reviews");
        assertThat(result.attributes()).isEmpty();
    }

    @Test
    void getProfile_profileAbsent_returnsEmptyNarrative() {
        final var provider = new WorkerProfileSkillProfileProvider(id -> Optional.empty());
        final var result = provider.getProfile("unknown", Set.of("legal"));
        assertThat(result.narrative()).isEmpty();
    }

    @Test
    void getProfile_nullNarrativeInProfile_returnsEmptyNarrative() {
        final var profile = new WorkerSkillProfile();
        profile.workerId = "bob";
        profile.narrative = null;

        final var provider = new WorkerProfileSkillProfileProvider(id -> Optional.of(profile));
        final var result = provider.getProfile("bob", Set.of());
        assertThat(result.narrative()).isEmpty();
    }
}
