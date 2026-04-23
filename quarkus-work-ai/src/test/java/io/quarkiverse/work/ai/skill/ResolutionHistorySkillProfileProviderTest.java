package io.quarkiverse.work.ai.skill;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.quarkiverse.work.runtime.model.WorkItem;
import io.quarkiverse.work.runtime.model.WorkItemStatus;
import io.quarkiverse.work.testing.InMemoryWorkItemStore;

class ResolutionHistorySkillProfileProviderTest {

    private WorkItem completedItem(final String assignee, final String category,
            final Instant completedAt) {
        final var wi = new WorkItem();
        wi.id = UUID.randomUUID();
        wi.assigneeId = assignee;
        wi.category = category;
        wi.status = WorkItemStatus.COMPLETED;
        wi.completedAt = completedAt;
        wi.title = "T";
        wi.createdBy = "test";
        return wi;
    }

    @Test
    void getProfile_aggregatesCategoryFrequency() {
        final var store = new InMemoryWorkItemStore();
        store.put(completedItem("alice", "legal", Instant.now()));
        store.put(completedItem("alice", "legal", Instant.now()));
        store.put(completedItem("alice", "finance", Instant.now()));

        final var provider = new ResolutionHistorySkillProfileProvider(store, 50);
        final var profile = provider.getProfile("alice", Set.of());

        assertThat(profile.narrative()).contains("legal×2");
        assertThat(profile.narrative()).contains("finance×1");
    }

    @Test
    void getProfile_noHistory_returnsEmptyNarrative() {
        final var store = new InMemoryWorkItemStore();
        final var provider = new ResolutionHistorySkillProfileProvider(store, 50);
        final var profile = provider.getProfile("nobody", Set.of());
        assertThat(profile.narrative()).isEmpty();
    }

    @Test
    void getProfile_respectsHistoryLimit() {
        final var store = new InMemoryWorkItemStore();
        final Instant base = Instant.now();
        for (int i = 0; i < 3; i++) {
            store.put(completedItem("alice", "legal", base.plusSeconds(i + 10)));
        }
        for (int i = 0; i < 5; i++) {
            store.put(completedItem("alice", "finance", base.plusSeconds(i)));
        }
        // limit=3 → only 3 most recent (all legal)
        final var provider = new ResolutionHistorySkillProfileProvider(store, 3);
        final var profile = provider.getProfile("alice", Set.of());
        assertThat(profile.narrative()).contains("legal×3");
        assertThat(profile.narrative()).doesNotContain("finance");
    }

    @Test
    void getProfile_nullCategory_skipped() {
        final var store = new InMemoryWorkItemStore();
        store.put(completedItem("alice", null, Instant.now()));
        final var provider = new ResolutionHistorySkillProfileProvider(store, 50);
        final var profile = provider.getProfile("alice", Set.of());
        assertThat(profile.narrative()).isEmpty();
    }

    @Test
    void attributes_containsFrequencyMap() {
        final var store = new InMemoryWorkItemStore();
        store.put(completedItem("alice", "legal", Instant.now()));
        store.put(completedItem("alice", "legal", Instant.now()));
        final var provider = new ResolutionHistorySkillProfileProvider(store, 50);
        final var profile = provider.getProfile("alice", Set.of());
        assertThat(profile.attributes()).containsEntry("legal", 2L);
    }
}
