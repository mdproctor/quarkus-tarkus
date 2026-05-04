package io.casehub.work.runtime.multiinstance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;

import io.casehub.work.api.GroupStatus;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemPriority;
import io.casehub.work.runtime.model.WorkItemRootView;
import io.casehub.work.runtime.model.WorkItemStatus;
import io.casehub.work.runtime.model.WorkItemTemplate;
import io.casehub.work.runtime.repository.WorkItemStore;
import io.casehub.work.runtime.service.WorkItemTemplateService;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class MultiInstanceInboxTest {

    @Inject
    WorkItemTemplateService templateService;

    @Inject
    WorkItemStore store;

    @Test
    @Transactional
    void standaloneWorkItemAppearsAsRootWithChildCountZero() {
        WorkItem item = new WorkItem();
        item.assigneeId = "alice-inbox-test";
        item.status = WorkItemStatus.PENDING;
        item.priority = WorkItemPriority.MEDIUM;
        item.title = "Standalone";
        item.createdBy = "test";
        item.persist();

        List<WorkItemRootView> roots = store.scanRoots("alice-inbox-test", List.of());
        assertThat(roots).anyMatch(r -> r.workItem().id.equals(item.id)
                && r.childCount() == 0
                && r.completedCount() == null
                && r.groupStatus() == null);
    }

    @Test
    @Transactional
    void multiInstanceParentAppearsAsRootWithAggregateStats() {
        WorkItemTemplate t = new WorkItemTemplate();
        t.name = "InboxGroup";
        t.candidateGroups = "inbox-group-" + java.util.UUID.randomUUID();
        t.createdBy = "test";
        t.instanceCount = 3;
        t.requiredCount = 2;
        t.persist();

        WorkItem parent = templateService.instantiate(t, null, null, "test");
        List<WorkItemRootView> roots = store.scanRoots(null, List.of(t.candidateGroups));

        assertThat(roots).anyMatch(r -> r.workItem().id.equals(parent.id)
                && r.childCount() == 3
                && r.completedCount() == 0
                && r.requiredCount() == 2
                && r.groupStatus() == GroupStatus.IN_PROGRESS);
    }

    @Test
    @Transactional
    void coordinatorParentVisibleWhenUserAssignedToChild() {
        String uniqueGroup = "child-group-" + java.util.UUID.randomUUID();
        WorkItemTemplate t = new WorkItemTemplate();
        t.name = "CoordinatorVisibility";
        t.candidateGroups = uniqueGroup;
        t.createdBy = "test";
        t.instanceCount = 2;
        t.requiredCount = 1;
        t.parentRole = "COORDINATOR";
        t.persist();

        WorkItem parent = templateService.instantiate(t, null, null, "test");

        // User in child-group has visibility into children, not coordinator parent directly
        List<WorkItemRootView> roots = store.scanRoots(null, List.of(uniqueGroup));

        assertThat(roots).anyMatch(r -> r.workItem().id.equals(parent.id));
    }

    @Test
    @Transactional
    void childrenDoNotAppearDirectlyInInbox() {
        String uniqueGroup = "no-children-" + java.util.UUID.randomUUID();
        WorkItemTemplate t = new WorkItemTemplate();
        t.name = "NoChildrenInInbox";
        t.candidateGroups = uniqueGroup;
        t.createdBy = "test";
        t.instanceCount = 2;
        t.requiredCount = 1;
        t.persist();

        templateService.instantiate(t, null, null, "test");

        List<WorkItemRootView> roots = store.scanRoots(null, List.of(uniqueGroup));

        // All returned items must have parentId == null
        assertThat(roots).allMatch(r -> r.workItem().parentId == null);
    }
}
