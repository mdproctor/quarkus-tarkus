package io.casehub.work.testing;

import io.casehub.work.issuetracker.model.WorkItemIssueLink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryIssueLinkStoreTest {

    private InMemoryIssueLinkStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryIssueLinkStore();
    }

    private WorkItemIssueLink link(final UUID workItemId, final String trackerType, final String externalRef) {
        final WorkItemIssueLink link = new WorkItemIssueLink();
        link.workItemId = workItemId;
        link.trackerType = trackerType;
        link.externalRef = externalRef;
        link.status = "open";
        link.linkedBy = "test";
        return link;
    }

    // ── save + findById ───────────────────────────────────────────────────────

    @Test
    void save_assignsIdAndLinkedAt_whenNull() {
        final WorkItemIssueLink link = link(UUID.randomUUID(), "github", "owner/repo#1");

        final WorkItemIssueLink saved = store.save(link);

        assertThat(saved.id).isNotNull();
        assertThat(saved.linkedAt).isNotNull();
    }

    @Test
    void save_preservesExistingIdAndLinkedAt() {
        final UUID id = UUID.randomUUID();
        final Instant ts = Instant.parse("2026-01-01T00:00:00Z");
        final WorkItemIssueLink link = link(UUID.randomUUID(), "github", "owner/repo#2");
        link.id = id;
        link.linkedAt = ts;

        store.save(link);

        assertThat(store.findById(id)).isPresent().get()
                .satisfies(l -> {
                    assertThat(l.id).isEqualTo(id);
                    assertThat(l.linkedAt).isEqualTo(ts);
                });
    }

    @Test
    void findById_returnsEmpty_whenNotFound() {
        assertThat(store.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void findById_returnsLink_afterSave() {
        final WorkItemIssueLink link = link(UUID.randomUUID(), "github", "owner/repo#3");
        store.save(link);

        assertThat(store.findById(link.id)).isPresent();
    }

    // ── findByWorkItemId ──────────────────────────────────────────────────────

    @Test
    void findByWorkItemId_returnsAllForWorkItem() {
        final UUID workItemId = UUID.randomUUID();
        store.save(link(workItemId, "github", "owner/repo#10"));
        store.save(link(workItemId, "github", "owner/repo#11"));
        store.save(link(UUID.randomUUID(), "github", "owner/repo#12")); // different WorkItem

        assertThat(store.findByWorkItemId(workItemId)).hasSize(2);
    }

    @Test
    void findByWorkItemId_returnsEmpty_whenNoLinks() {
        assertThat(store.findByWorkItemId(UUID.randomUUID())).isEmpty();
    }

    // ── findByRef ─────────────────────────────────────────────────────────────

    @Test
    void findByRef_returnsLink_whenExists() {
        final UUID workItemId = UUID.randomUUID();
        store.save(link(workItemId, "github", "owner/repo#20"));

        final Optional<WorkItemIssueLink> result = store.findByRef(workItemId, "github", "owner/repo#20");

        assertThat(result).isPresent();
        assertThat(result.get().workItemId).isEqualTo(workItemId);
    }

    @Test
    void findByRef_returnsEmpty_whenNotFound() {
        assertThat(store.findByRef(UUID.randomUUID(), "github", "owner/repo#99")).isEmpty();
    }

    @Test
    void findByRef_doesNotMatch_wrongWorkItem() {
        store.save(link(UUID.randomUUID(), "github", "owner/repo#30"));

        assertThat(store.findByRef(UUID.randomUUID(), "github", "owner/repo#30")).isEmpty();
    }

    // ── findByTrackerRef ──────────────────────────────────────────────────────

    @Test
    void findByTrackerRef_returnsAllMatchingLinks() {
        final UUID workItemId1 = UUID.randomUUID();
        final UUID workItemId2 = UUID.randomUUID();
        store.save(link(workItemId1, "github", "owner/repo#40"));
        store.save(link(workItemId2, "github", "owner/repo#40"));
        store.save(link(UUID.randomUUID(), "github", "owner/repo#41")); // different ref

        final List<WorkItemIssueLink> results = store.findByTrackerRef("github", "owner/repo#40");

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(l -> "owner/repo#40".equals(l.externalRef));
    }

    @Test
    void findByTrackerRef_returnsEmpty_whenNoMatches() {
        assertThat(store.findByTrackerRef("github", "owner/repo#999")).isEmpty();
    }

    @Test
    void findByTrackerRef_doesNotMatch_differentTrackerType() {
        store.save(link(UUID.randomUUID(), "github", "owner/repo#50"));

        assertThat(store.findByTrackerRef("jira", "owner/repo#50")).isEmpty();
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_removesLink() {
        final WorkItemIssueLink link = store.save(link(UUID.randomUUID(), "github", "owner/repo#60"));

        store.delete(link);

        assertThat(store.findById(link.id)).isEmpty();
    }

    @Test
    void delete_doesNotAffectOtherLinks() {
        final UUID workItemId = UUID.randomUUID();
        final WorkItemIssueLink a = store.save(link(workItemId, "github", "owner/repo#70"));
        final WorkItemIssueLink b = store.save(link(workItemId, "github", "owner/repo#71"));

        store.delete(a);

        assertThat(store.findById(b.id)).isPresent();
        assertThat(store.findByWorkItemId(workItemId)).hasSize(1);
    }

    // ── clear ─────────────────────────────────────────────────────────────────

    @Test
    void clear_removesAllLinks() {
        store.save(link(UUID.randomUUID(), "github", "owner/repo#80"));
        store.save(link(UUID.randomUUID(), "github", "owner/repo#81"));

        store.clear();

        assertThat(store.findByTrackerRef("github", "owner/repo#80")).isEmpty();
        assertThat(store.findByTrackerRef("github", "owner/repo#81")).isEmpty();
    }
}
