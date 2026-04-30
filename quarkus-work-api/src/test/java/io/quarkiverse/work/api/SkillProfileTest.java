package io.casehub.work.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class SkillProfileTest {

    @Test
    void ofNarrative_setsNarrativeAndEmptyAttributes() {
        final var p = SkillProfile.ofNarrative("legal expert");
        assertThat(p.narrative()).isEqualTo("legal expert");
        assertThat(p.attributes()).isEmpty();
    }

    @Test
    void fullConstructor_storesAllFields() {
        final var attrs = Map.<String, Object> of("count", 42, "rate", 0.95);
        final var p = new SkillProfile("NDA specialist", attrs);
        assertThat(p.narrative()).isEqualTo("NDA specialist");
        assertThat(p.attributes()).containsEntry("count", 42);
    }

    @Test
    void ofNarrative_nullNarrative_stored() {
        final var p = SkillProfile.ofNarrative(null);
        assertThat(p.narrative()).isNull();
        assertThat(p.attributes()).isEmpty();
    }

    @Test
    void ofNarrative_emptyString_stored() {
        final var p = SkillProfile.ofNarrative("");
        assertThat(p.narrative()).isEmpty();
    }

    @Test
    void skillProfileProvider_canImplementWithLambda() {
        SkillProfileProvider p = (workerId, caps) -> SkillProfile.ofNarrative("skills: " + String.join(", ", caps));
        final var profile = p.getProfile("alice", Set.of("legal", "nda"));
        assertThat(profile.narrative()).contains("legal");
    }

    @Test
    void skillMatcher_canImplementWithLambda() {
        SkillMatcher m = (profile, ctx) -> profile.narrative().length();
        final var ctx = new SelectionContext("legal", null, null, null, null, null, null);
        assertThat(m.score(SkillProfile.ofNarrative("expert"), ctx)).isEqualTo(6.0);
    }
}
