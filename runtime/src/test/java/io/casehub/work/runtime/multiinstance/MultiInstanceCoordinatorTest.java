package io.casehub.work.runtime.multiinstance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemSpawnGroup;
import io.casehub.work.runtime.model.WorkItemStatus;
import io.casehub.work.runtime.model.WorkItemTemplate;
import io.casehub.work.runtime.service.WorkItemService;
import io.casehub.work.runtime.service.WorkItemTemplateService;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class MultiInstanceCoordinatorTest {

    @Inject
    WorkItemTemplateService templateService;

    @Inject
    WorkItemService workItemService;

    @Inject
    MultiInstanceSpawnService spawnService;

    private UUID createGroupAndGetParentId(int instanceCount, int requiredCount) {
        return inTx(() -> {
            WorkItemTemplate t = new WorkItemTemplate();
            t.name = "CoordinatorTest";
            t.candidateGroups = "testers";
            t.createdBy = "test";
            t.instanceCount = instanceCount;
            t.requiredCount = requiredCount;
            t.persist();
            return templateService.instantiate(t, null, null, "test").id;
        });
    }

    @Test
    void parentCompletesWhenMChildrenComplete() {
        UUID parentId = createGroupAndGetParentId(3, 2);
        List<UUID> childIds = inTx(() -> WorkItem.<WorkItem> list("parentId", parentId).stream().map(w -> w.id).toList());

        inTx(() -> workItemService.claim(childIds.get(0), "alice"));
        inTx(() -> workItemService.start(childIds.get(0), "alice"));
        inTx(() -> workItemService.complete(childIds.get(0), "alice", "approved"));

        inTx(() -> workItemService.claim(childIds.get(1), "bob"));
        inTx(() -> workItemService.start(childIds.get(1), "bob"));
        inTx(() -> workItemService.complete(childIds.get(1), "bob", "approved"));

        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            WorkItem parent = inTx(() -> WorkItem.findById(parentId));
            assertThat(parent.status).isEqualTo(WorkItemStatus.COMPLETED);
        });
    }

    @Test
    void parentRejectedWhenGroupCannotReachM() {
        UUID parentId = createGroupAndGetParentId(3, 2);
        List<UUID> childIds = inTx(() -> WorkItem.<WorkItem> list("parentId", parentId).stream().map(w -> w.id).toList());

        // 2 rejections — remaining(1) < needed(2), group fails
        inTx(() -> workItemService.claim(childIds.get(0), "alice"));
        inTx(() -> workItemService.start(childIds.get(0), "alice"));
        inTx(() -> workItemService.reject(childIds.get(0), "alice", "denied"));

        inTx(() -> workItemService.claim(childIds.get(1), "bob"));
        inTx(() -> workItemService.start(childIds.get(1), "bob"));
        inTx(() -> workItemService.reject(childIds.get(1), "bob", "denied"));

        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            WorkItem parent = inTx(() -> WorkItem.findById(parentId));
            assertThat(parent.status).isEqualTo(WorkItemStatus.REJECTED);
        });
    }

    @Test
    void cancelPolicyRemovesRemainingChildrenAfterThreshold() {
        UUID parentId = inTx(() -> {
            WorkItemTemplate t = new WorkItemTemplate();
            t.name = "CancelTest";
            t.candidateGroups = "testers";
            t.createdBy = "test";
            t.instanceCount = 5;
            t.requiredCount = 2;
            t.onThresholdReached = "CANCEL";
            t.persist();
            return templateService.instantiate(t, null, null, "test").id;
        });

        List<UUID> childIds = inTx(() -> WorkItem.<WorkItem> list("parentId", parentId).stream().map(w -> w.id).toList());

        inTx(() -> workItemService.claim(childIds.get(0), "alice"));
        inTx(() -> workItemService.start(childIds.get(0), "alice"));
        inTx(() -> workItemService.complete(childIds.get(0), "alice", "approved"));

        inTx(() -> workItemService.claim(childIds.get(1), "bob"));
        inTx(() -> workItemService.start(childIds.get(1), "bob"));
        inTx(() -> workItemService.complete(childIds.get(1), "bob", "approved"));

        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            long cancelled = inTx(() -> WorkItem.count(
                    "parentId = ?1 AND status = ?2", parentId, WorkItemStatus.CANCELLED));
            assertThat(cancelled).isEqualTo(3);
        });
    }

    @Test
    void leavePolicyDoesNotCancelRemainingChildren() {
        UUID parentId = inTx(() -> {
            WorkItemTemplate t = new WorkItemTemplate();
            t.name = "KeepTest";
            t.candidateGroups = "testers";
            t.createdBy = "test";
            t.instanceCount = 3;
            t.requiredCount = 2;
            t.onThresholdReached = "KEEP";
            t.persist();
            return templateService.instantiate(t, null, null, "test").id;
        });

        List<UUID> childIds = inTx(() -> WorkItem.<WorkItem> list("parentId", parentId).stream().map(w -> w.id).toList());

        inTx(() -> workItemService.claim(childIds.get(0), "alice"));
        inTx(() -> workItemService.start(childIds.get(0), "alice"));
        inTx(() -> workItemService.complete(childIds.get(0), "alice", "approved"));

        inTx(() -> workItemService.claim(childIds.get(1), "bob"));
        inTx(() -> workItemService.start(childIds.get(1), "bob"));
        inTx(() -> workItemService.complete(childIds.get(1), "bob", "approved"));

        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            WorkItem parent = inTx(() -> WorkItem.findById(parentId));
            assertThat(parent.status).isEqualTo(WorkItemStatus.COMPLETED);
        });

        WorkItem third = inTx(() -> WorkItem.findById(childIds.get(2)));
        assertThat(third.status).isEqualTo(WorkItemStatus.PENDING);
    }

    @Test
    void policyTriggeredIsIdempotent() {
        UUID parentId = createGroupAndGetParentId(3, 2);
        List<UUID> childIds = inTx(() -> WorkItem.<WorkItem> list("parentId", parentId).stream().map(w -> w.id).toList());

        // Complete all 3 (exceeds M=2) — parent should complete exactly once
        for (UUID childId : childIds) {
            inTx(() -> workItemService.claim(childId, "alice"));
            inTx(() -> workItemService.start(childId, "alice"));
            inTx(() -> workItemService.complete(childId, "alice", "approved"));
        }

        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            WorkItemSpawnGroup group = inTx(() -> WorkItemSpawnGroup.findMultiInstanceByParentId(parentId));
            assertThat(group.policyTriggered).isTrue();
        });

        WorkItem parent = inTx(() -> WorkItem.findById(parentId));
        assertThat(parent.status).isEqualTo(WorkItemStatus.COMPLETED);
    }

    @Test
    void createGroup_withCallerRef_setsOnParentOnly() {
        final String callerRef = "case:550e8400-e29b-41d4-a716-446655440000/pi:plan-gate";

        final UUID parentId = inTx(() -> {
            WorkItemTemplate t = new WorkItemTemplate();
            t.name = "CallerRefTest";
            t.candidateGroups = "testers";
            t.createdBy = "test";
            t.instanceCount = 3;
            t.requiredCount = 2;
            t.persist();
            return spawnService.createGroup(t, null, "test", callerRef).id;
        });

        final WorkItem parent = inTx(() -> WorkItem.findById(parentId));
        assertThat(parent.callerRef).isEqualTo(callerRef);

        final List<WorkItem> children = inTx(() ->
            WorkItem.<WorkItem>list("parentId", parentId));
        assertThat(children).hasSize(3);
        assertThat(children).allSatisfy(child ->
            assertThat(child.callerRef).isNull());
    }

    @Transactional
    <T> T inTx(Supplier<T> s) {
        return s.get();
    }

    @Transactional
    void inTx(Runnable r) {
        r.run();
    }
}
