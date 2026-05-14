package io.casehub.work.runtime.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.work.runtime.model.AuditEntry;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemSpawnGroup;
import io.casehub.work.runtime.model.WorkItemTemplate;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for WorkItemTemplateService.instantiate() callerRef overload.
 * Requires CDI + JPA — see WorkItemTemplateServiceTest for pure-unit mapping tests.
 */
@QuarkusTest
class WorkItemTemplateInstantiateTest {

    @Inject
    WorkItemTemplateService templateService;

    @BeforeEach
    @Transactional
    void cleanup() {
        AuditEntry.deleteAll();
        WorkItemSpawnGroup.deleteAll();
        WorkItem.deleteAll();
        WorkItemTemplate.deleteAll();
    }

    @Test
    void instantiate_setsCallerRef_onCreatedWorkItem() {
        final WorkItemTemplate template = persistedTemplate("IRB Review", null);
        final String callerRef = "case:550e8400-e29b-41d4-a716-446655440000/pi:irb-gate";

        final WorkItem workItem =
            templateService.instantiate(template, null, null, "system:engine", callerRef);

        assertThat(workItem.callerRef).isEqualTo(callerRef);
    }

    @Test
    void instantiate_multiInstanceTemplate_ignoresCallerRef() {
        final WorkItemTemplate template = persistedTemplate("Parallel Review", 3);
        final String callerRef = "case:550e8400-e29b-41d4-a716-446655440000/pi:review-gate";

        final WorkItem parent =
            templateService.instantiate(template, null, null, "system:engine", callerRef);

        assertThat(parent).isNotNull();
        assertThat(parent.callerRef).isNull();
    }

    @Test
    void instantiate_4arg_callerRefIsNull() {
        final WorkItemTemplate template = persistedTemplate("Human Task", null);

        final WorkItem workItem =
            templateService.instantiate(template, null, null, "system");

        assertThat(workItem.callerRef).isNull();
    }

    @Transactional
    WorkItemTemplate persistedTemplate(final String name, final Integer instanceCount) {
        final WorkItemTemplate t = new WorkItemTemplate();
        t.name = name;
        t.candidateGroups = "reviewers";
        t.createdBy = "admin";
        t.instanceCount = instanceCount;
        t.persist();
        return t;
    }
}
