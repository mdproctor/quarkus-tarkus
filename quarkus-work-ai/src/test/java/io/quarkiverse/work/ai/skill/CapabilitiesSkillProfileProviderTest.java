package io.quarkiverse.work.ai.skill;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

import io.quarkiverse.work.api.SkillProfileProvider;

class CapabilitiesSkillProfileProviderTest {

    private final CapabilitiesSkillProfileProvider provider = new CapabilitiesSkillProfileProvider();

    @Test
    void getProfile_joinsCapabilitiesIntoNarrative() {
        final var profile = provider.getProfile("alice", Set.of("legal", "nda", "gdpr"));
        assertThat(profile.narrative()).contains("legal");
        assertThat(profile.narrative()).contains("nda");
        assertThat(profile.narrative()).contains("gdpr");
    }

    @Test
    void getProfile_emptyCapabilities_returnsEmptyNarrative() {
        final var profile = provider.getProfile("alice", Set.of());
        assertThat(profile.narrative()).isEmpty();
        assertThat(profile.attributes()).isEmpty();
    }

    @Test
    void getProfile_nullCapabilities_returnsEmptyNarrative() {
        final var profile = provider.getProfile("alice", null);
        assertThat(profile.narrative()).isEmpty();
    }

    @Test
    void getProfile_attributesAlwaysEmpty() {
        final var profile = provider.getProfile("bob", Set.of("approval"));
        assertThat(profile.attributes()).isEmpty();
    }

    @Test
    void implementsSkillProfileProvider() {
        assertThat(provider).isInstanceOf(SkillProfileProvider.class);
    }
}
