package io.quarkiverse.work.runtime.multiinstance;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;

import io.quarkiverse.work.runtime.model.WorkItem;
import io.quarkiverse.work.runtime.model.WorkItemTemplate;
import io.quarkiverse.work.runtime.service.WorkItemService;
import io.quarkiverse.work.runtime.service.WorkItemTemplateService;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class MultiInstanceClaimGuardTest {

    @Inject
    WorkItemTemplateService templateService;

    @Inject
    WorkItemService workItemService;

    private UUID createGroupAndGetParentId(final boolean allowSameAssignee) {
        return inTx(() -> {
            WorkItemTemplate t = new WorkItemTemplate();
            t.name = "Claim Guard Test";
            t.candidateGroups = "reviewers";
            t.createdBy = "test";
            t.instanceCount = 3;
            t.requiredCount = 2;
            t.allowSameAssignee = allowSameAssignee;
            t.persist();
            return templateService.instantiate(t, null, null, "test").id;
        });
    }

    @Test
    void guardEnforced_sameAssigneeCannotClaimTwoInstances() {
        UUID parentId = createGroupAndGetParentId(false);
        List<UUID> childIds = inTx(() -> WorkItem.<WorkItem> list("parentId", parentId).stream().map(w -> w.id).toList());

        // Claim first instance successfully
        inTx(() -> workItemService.claim(childIds.get(0), "alice"));

        // Second claim by same person should fail
        assertThatThrownBy(() -> inTx(() -> workItemService.claim(childIds.get(1), "alice")))
                .hasMessageContaining("already hold another instance");
    }

    @Test
    void guardDisabled_sameAssigneeCanClaimMultipleInstances() {
        UUID parentId = createGroupAndGetParentId(true);
        List<UUID> childIds = inTx(() -> WorkItem.<WorkItem> list("parentId", parentId).stream().map(w -> w.id).toList());

        inTx(() -> workItemService.claim(childIds.get(0), "alice"));
        assertThatCode(() -> inTx(() -> workItemService.claim(childIds.get(1), "alice")))
                .doesNotThrowAnyException();
    }

    @Test
    void differentAssigneesCanAlwaysClaim() {
        UUID parentId = createGroupAndGetParentId(false);
        List<UUID> childIds = inTx(() -> WorkItem.<WorkItem> list("parentId", parentId).stream().map(w -> w.id).toList());

        inTx(() -> workItemService.claim(childIds.get(0), "alice"));
        assertThatCode(() -> inTx(() -> workItemService.claim(childIds.get(1), "bob")))
                .doesNotThrowAnyException();
    }

    @Transactional
    <T> T inTx(final Supplier<T> s) {
        return s.get();
    }

    @Transactional
    void inTx(final Runnable r) {
        r.run();
    }
}
