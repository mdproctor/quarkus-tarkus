package io.quarkiverse.workitems.ai.skill;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.quarkiverse.work.api.SkillProfile;
import io.quarkiverse.work.api.SkillProfileProvider;

class CompositeSkillProfileProviderTest {

    private static final Set<String> CAPS = Set.of("legal", "nda-review");

    @Test
    void getProfile_singleDelegate_returnsItsNarrative() {
        final CompositeSkillProfileProvider composite = composite(
                (id, caps) -> SkillProfile.ofNarrative("NDA specialist"));

        final SkillProfile profile = composite.getProfile("alice", CAPS);
        assertThat(profile.narrative()).isEqualTo("NDA specialist");
    }

    @Test
    void getProfile_twoDelegates_concatenatesNarrativesWithNewline() {
        final CompositeSkillProfileProvider composite = composite(
                (id, caps) -> SkillProfile.ofNarrative("NDA specialist"),
                (id, caps) -> SkillProfile.ofNarrative("legal, nda-review"));

        final SkillProfile profile = composite.getProfile("alice", CAPS);
        assertThat(profile.narrative()).isEqualTo("NDA specialist\nlegal, nda-review");
    }

    @Test
    void getProfile_blankNarrativeFromDelegate_isSkipped() {
        final CompositeSkillProfileProvider composite = composite(
                (id, caps) -> SkillProfile.ofNarrative("  "),
                (id, caps) -> SkillProfile.ofNarrative("legal"));

        final SkillProfile profile = composite.getProfile("alice", CAPS);
        assertThat(profile.narrative()).isEqualTo("legal");
    }

    @Test
    void getProfile_allBlankNarratives_returnsNullNarrative() {
        final CompositeSkillProfileProvider composite = composite(
                (id, caps) -> SkillProfile.ofNarrative(""),
                (id, caps) -> SkillProfile.ofNarrative("  "));

        final SkillProfile profile = composite.getProfile("alice", CAPS);
        assertThat(profile.narrative()).isNull();
    }

    @Test
    void getProfile_noDelegates_returnsEmptyProfile() {
        final CompositeSkillProfileProvider composite = composite();

        final SkillProfile profile = composite.getProfile("alice", CAPS);
        assertThat(profile.narrative()).isNull();
        assertThat(profile.attributes()).isEmpty();
    }

    @Test
    void getProfile_attributesUnioned_acrossDelegates() {
        final CompositeSkillProfileProvider composite = composite(
                (id, caps) -> new SkillProfile("a", Map.of("level", "senior")),
                (id, caps) -> new SkillProfile("b", Map.of("domain", "legal")));

        final SkillProfile profile = composite.getProfile("alice", CAPS);
        assertThat(profile.attributes()).containsEntry("level", "senior");
        assertThat(profile.attributes()).containsEntry("domain", "legal");
    }

    @Test
    void getProfile_workerIdPassedThroughToDelegates() {
        final List<String> seen = new java.util.ArrayList<>();
        final CompositeSkillProfileProvider composite = composite(
                (id, caps) -> {
                    seen.add(id);
                    return SkillProfile.ofNarrative("ok");
                });

        composite.getProfile("bob", CAPS);
        assertThat(seen).containsExactly("bob");
    }

    private CompositeSkillProfileProvider composite(final SkillProfileProvider... providers) {
        return new CompositeSkillProfileProvider(List.of(providers));
    }
}
