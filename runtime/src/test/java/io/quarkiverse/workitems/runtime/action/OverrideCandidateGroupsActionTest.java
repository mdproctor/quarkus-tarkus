package io.quarkiverse.workitems.runtime.action;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.quarkiverse.workitems.runtime.model.WorkItem;

class OverrideCandidateGroupsActionTest {

    private final OverrideCandidateGroupsAction action = new OverrideCandidateGroupsAction();

    @Test
    void type_isOVERRIDE_CANDIDATE_GROUPS() {
        assertThat(action.type()).isEqualTo("OVERRIDE_CANDIDATE_GROUPS");
    }

    @Test
    void apply_setsCandidateGroups() {
        final var wi = new WorkItem();
        wi.id = UUID.randomUUID();
        action.apply(wi, Map.of("groups", "finance-team,legal-team"));
        assertThat(wi.candidateGroups).isEqualTo("finance-team,legal-team");
    }

    @Test
    void apply_blankGroups_noChange() {
        final var wi = new WorkItem();
        wi.id = UUID.randomUUID();
        wi.candidateGroups = "original";
        action.apply(wi, Map.of("groups", "  "));
        assertThat(wi.candidateGroups).isEqualTo("original");
    }
}
