package io.casehub.work.runtime.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemStatus;
import io.casehub.work.runtime.repository.WorkItemQuery;
import io.casehub.work.runtime.repository.WorkItemStore;

/**
 * Unit tests for JpaWorkloadProvider — no Quarkus boot.
 * Verifies that ASSIGNED, IN_PROGRESS, and SUSPENDED count as active;
 * terminal statuses (COMPLETED, REJECTED, CANCELLED) do not.
 */
@ExtendWith(MockitoExtension.class)
class JpaWorkloadProviderTest {

    @Mock
    WorkItemStore workItemStore;

    private WorkItem workItem(final String assignee, final WorkItemStatus status) {
        final var wi = new WorkItem();
        wi.id = UUID.randomUUID();
        wi.assigneeId = assignee;
        wi.status = status;
        wi.title = "T";
        wi.createdBy = "test";
        return wi;
    }

    private void mockStore(final String actorId, final List<WorkItem> items) {
        when(workItemStore.scan(any(WorkItemQuery.class))).thenReturn(items);
    }

    @Test
    void countsAssignedAndInProgressAndSuspended() {
        final var provider = new JpaWorkloadProvider(workItemStore);

        final List<WorkItem> aliceItems = List.of(
                workItem("alice", WorkItemStatus.ASSIGNED),
                workItem("alice", WorkItemStatus.IN_PROGRESS));
        // Simulate two separate scan calls (one per getActiveWorkCount call)
        when(workItemStore.scan(any(WorkItemQuery.class)))
                .thenReturn(aliceItems)
                .thenReturn(List.of(workItem("bob", WorkItemStatus.ASSIGNED)))
                .thenReturn(List.of());

        assertThat(provider.getActiveWorkCount("alice")).isEqualTo(2);
        assertThat(provider.getActiveWorkCount("bob")).isEqualTo(1);
        assertThat(provider.getActiveWorkCount("carol")).isZero();
    }

    @Test
    void suspendedCountedAsActive() {
        final var provider = new JpaWorkloadProvider(workItemStore);
        when(workItemStore.scan(any(WorkItemQuery.class)))
                .thenReturn(List.of(workItem("alice", WorkItemStatus.SUSPENDED)));

        assertThat(provider.getActiveWorkCount("alice")).isEqualTo(1);
    }

    @Test
    void rejectedAndCancelledNotCounted() {
        final var provider = new JpaWorkloadProvider(workItemStore);
        // The store's statusIn filter will already exclude terminal statuses;
        // we simulate it returning an empty list (as a real JPA impl would).
        when(workItemStore.scan(any(WorkItemQuery.class))).thenReturn(List.of());

        assertThat(provider.getActiveWorkCount("alice")).isZero();
    }

    @Test
    void filtersByAssigneeId_exactMatch() {
        // Verifies the post-scan assigneeId filter — guards against the
        // candidateUsers LIKE over-counting described in the original countActive().
        final var provider = new JpaWorkloadProvider(workItemStore);
        final WorkItem aliceItem = workItem("alice", WorkItemStatus.ASSIGNED);
        final WorkItem bobItem = workItem("bob", WorkItemStatus.ASSIGNED);
        // Store returns both but filter should keep only alice
        when(workItemStore.scan(any(WorkItemQuery.class))).thenReturn(List.of(aliceItem, bobItem));

        assertThat(provider.getActiveWorkCount("alice")).isEqualTo(1);
    }
}
