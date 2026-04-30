package io.casehub.work.runtime.multiinstance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.casehub.work.api.MultiInstanceConfig;
import io.casehub.work.api.MultiInstanceContext;
import io.casehub.work.runtime.model.WorkItem;

class InstanceAssignmentStrategyTest {

    private static WorkItem item() {
        WorkItem w = new WorkItem();
        return w;
    }

    private static WorkItem parent(String candidateGroups, String candidateUsers) {
        WorkItem p = new WorkItem();
        p.candidateGroups = candidateGroups;
        p.candidateUsers = candidateUsers;
        return p;
    }

    // --- PoolAssignmentStrategy ---

    @Test
    void pool_copiesCandidateGroupsToAllInstances() {
        var strategy = new PoolAssignmentStrategy();
        var parent = parent("reviewers,approvers", null);
        var instances = List.of(item(), item(), item());
        strategy.assign((List) instances, new MultiInstanceContext(parent,
                new MultiInstanceConfig(3, 2, null, "pool", null, false, null)));

        assertThat(instances).allMatch(i -> "reviewers,approvers".equals(i.candidateGroups));
    }

    @Test
    void pool_copiesCandidateUsersToAllInstances() {
        var strategy = new PoolAssignmentStrategy();
        var parent = parent(null, "alice,bob,carol");
        var instances = List.of(item(), item());
        strategy.assign((List) instances, new MultiInstanceContext(parent,
                new MultiInstanceConfig(2, 1, null, "pool", null, false, null)));

        assertThat(instances).allMatch(i -> "alice,bob,carol".equals(i.candidateUsers));
    }

    // --- ExplicitListAssignmentStrategy ---

    @Test
    void explicit_assignsEachInstanceToCorrespondingAssignee() {
        var strategy = new ExplicitListAssignmentStrategy();
        var instances = List.of(item(), item(), item());
        strategy.assign((List) instances, new MultiInstanceContext(new WorkItem(),
                new MultiInstanceConfig(3, 2, null, "explicit", null, false,
                        List.of("alice", "bob", "carol"))));

        assertThat(instances.get(0).assigneeId).isEqualTo("alice");
        assertThat(instances.get(1).assigneeId).isEqualTo("bob");
        assertThat(instances.get(2).assigneeId).isEqualTo("carol");
    }

    @Test
    void explicit_throwsWhenListSizeMismatch() {
        var strategy = new ExplicitListAssignmentStrategy();
        var instances = List.of(item(), item());
        assertThatThrownBy(() -> strategy.assign((List) instances,
                new MultiInstanceContext(new WorkItem(),
                        new MultiInstanceConfig(2, 1, null, "explicit", null, false,
                                List.of("alice")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("explicitAssignees");
    }

    // --- CompositeInstanceAssignmentStrategy ---

    @Test
    void composite_appliesStrategiesInOrder() {
        // Pool sets candidateGroups; Explicit then overrides assigneeId
        var pool = new PoolAssignmentStrategy();
        var explicit = new ExplicitListAssignmentStrategy();
        var composite = new CompositeInstanceAssignmentStrategy(List.of(pool, explicit));

        var parent = parent("reviewers", null);
        var instances = List.of(item(), item());
        composite.assign((List) instances, new MultiInstanceContext(parent,
                new MultiInstanceConfig(2, 1, null, "composite", null, false,
                        List.of("alice", "bob"))));

        // Pool ran first: candidateGroups set; Explicit ran second: assigneeId set
        assertThat(instances).allMatch(i -> "reviewers".equals(i.candidateGroups));
        assertThat(instances.get(0).assigneeId).isEqualTo("alice");
        assertThat(instances.get(1).assigneeId).isEqualTo("bob");
    }
}
