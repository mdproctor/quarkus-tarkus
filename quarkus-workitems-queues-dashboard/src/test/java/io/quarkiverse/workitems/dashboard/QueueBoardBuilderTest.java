package io.quarkiverse.workitems.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.quarkiverse.workitems.runtime.model.LabelPersistence;
import io.quarkiverse.workitems.runtime.model.WorkItem;
import io.quarkiverse.workitems.runtime.model.WorkItemLabel;

class QueueBoardBuilderTest {

    @Test
    void tier_urgentInferredLabel_returnsUrgent() {
        final var wi = workItemWithLabels("review/urgent", "review/urgent/unassigned");
        assertThat(QueueBoardBuilder.tier(wi)).isEqualTo("review/urgent");
    }

    @Test
    void state_unassignedLabel_returnsUnassigned() {
        final var wi = workItemWithLabels("review/urgent", "review/urgent/unassigned");
        assertThat(QueueBoardBuilder.state(wi)).isEqualTo("unassigned");
    }

    @Test
    void state_claimedLabel_returnsClaimed() {
        final var wi = workItemWithLabels("review/standard", "review/standard/claimed");
        assertThat(QueueBoardBuilder.state(wi)).isEqualTo("claimed");
    }

    @Test
    void state_activeLabel_returnsActive() {
        final var wi = workItemWithLabels("review/routine", "review/routine/active");
        assertThat(QueueBoardBuilder.state(wi)).isEqualTo("active");
    }

    @Test
    void tier_noLabels_returnsNull() {
        assertThat(QueueBoardBuilder.tier(new WorkItem())).isNull();
    }

    @Test
    void build_threeItems_correctlyBucketed() {
        final var urgent = workItemWithLabels("review/urgent", "review/urgent/unassigned");
        urgent.title = "Security advisory";
        final var standard = workItemWithLabels("review/standard", "review/standard/unassigned");
        standard.title = "Release notes";
        final var routine = workItemWithLabels("review/routine", "review/routine/unassigned");
        routine.title = "Tutorial";

        final var grid = QueueBoardBuilder.build(List.of(urgent, standard, routine));

        assertThat(grid.get("review/urgent").get("unassigned")).containsExactly("Security advisory");
        assertThat(grid.get("review/standard").get("unassigned")).containsExactly("Release notes");
        assertThat(grid.get("review/routine").get("unassigned")).containsExactly("Tutorial");
        assertThat(grid.get("review/urgent").get("claimed")).isEmpty();
    }

    @Test
    void build_itemWithNoTier_omittedFromGrid() {
        final var wi = new WorkItem();
        wi.title = "No tier item";
        final var grid = QueueBoardBuilder.build(List.of(wi));
        assertThat(grid.values().stream().flatMap(m -> m.values().stream()).allMatch(List::isEmpty)).isTrue();
    }

    @Test
    void formatCell_empty_returnsDash() {
        assertThat(QueueBoardBuilder.formatCell(List.of())).isEqualTo("\u2014");
    }

    @Test
    void formatCell_oneItem_returnsTitle() {
        assertThat(QueueBoardBuilder.formatCell(List.of("Security advisory"))).isEqualTo("Security advisory");
    }

    @Test
    void formatCell_multipleItems_showsPlusMore() {
        final var titles = List.of("First", "Second", "Third");
        assertThat(QueueBoardBuilder.formatCell(titles)).contains("(+2 more)");
    }

    private WorkItem workItemWithLabels(final String... paths) {
        final var wi = new WorkItem();
        for (final String path : paths) {
            wi.labels.add(new WorkItemLabel(path, LabelPersistence.INFERRED, "test-filter"));
        }
        return wi;
    }
}
