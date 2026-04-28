package io.quarkiverse.work.runtime.multiinstance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;

import io.quarkiverse.work.runtime.model.WorkItem;
import io.quarkiverse.work.runtime.model.WorkItemRelation;
import io.quarkiverse.work.runtime.model.WorkItemRelationType;
import io.quarkiverse.work.runtime.model.WorkItemSpawnGroup;
import io.quarkiverse.work.runtime.model.WorkItemTemplate;
import io.quarkiverse.work.runtime.service.WorkItemTemplateService;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class MultiInstanceCreateTest {

    @Inject
    WorkItemTemplateService templateService;

    @Test
    @Transactional
    void instantiatingMultiInstanceTemplateCreatesParentAndNChildren() {
        WorkItemTemplate template = new WorkItemTemplate();
        template.name = "Approval";
        template.category = "approval";
        template.candidateGroups = "reviewers";
        template.createdBy = "test";
        template.instanceCount = 3;
        template.requiredCount = 2;
        template.persist();

        WorkItem parent = templateService.instantiate(template, null, null, "test");

        assertThat(parent.parentId).isNull(); // parent has no parent
        assertThat(parent.id).isNotNull();

        // Three children should exist
        List<WorkItem> children = WorkItem.list("parentId", parent.id);
        assertThat(children).hasSize(3);

        // All children have PART_OF relation to parent
        children.forEach(child -> {
            assertThat(child.parentId).isEqualTo(parent.id);
            long relations = WorkItemRelation.count(
                    "sourceId = ?1 AND targetId = ?2 AND relationType = ?3",
                    child.id, parent.id, WorkItemRelationType.PART_OF);
            assertThat(relations).isEqualTo(1);
        });

        // Spawn group created with policy
        WorkItemSpawnGroup group = WorkItemSpawnGroup.findMultiInstanceByParentId(parent.id);
        assertThat(group).isNotNull();
        assertThat(group.instanceCount).isEqualTo(3);
        assertThat(group.requiredCount).isEqualTo(2);
        assertThat(group.completedCount).isZero();
        assertThat(group.policyTriggered).isFalse();
    }

    @Test
    @Transactional
    void nonMultiInstanceTemplateCreatesOneWorkItem() {
        WorkItemTemplate template = new WorkItemTemplate();
        template.name = "Simple Task";
        template.createdBy = "test";
        template.persist();

        WorkItem item = templateService.instantiate(template, null, null, "test");

        assertThat(item.parentId).isNull();
        assertThat(WorkItem.count("parentId", item.id)).isZero();
    }
}
