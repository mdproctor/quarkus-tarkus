package io.quarkiverse.workitems.runtime.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.quarkiverse.workitems.runtime.model.LabelPersistence;
import io.quarkiverse.workitems.runtime.model.WorkItemCreateRequest;
import io.quarkiverse.workitems.runtime.model.WorkItemPriority;
import io.quarkiverse.workitems.runtime.model.WorkItemTemplate;

/**
 * Pure unit tests for WorkItemTemplate → WorkItemCreateRequest mapping.
 * No Quarkus, no DB — exercises the template-to-request conversion logic only.
 */
class WorkItemTemplateServiceTest {

    // ── toCreateRequest: template defaults ────────────────────────────────────

    @Test
    void toCreateRequest_usesTemplateNameAsTitle_whenNoOverride() {
        final WorkItemTemplate t = template("Contract Review");
        final WorkItemCreateRequest req = WorkItemTemplateService.toCreateRequest(t, null, null, "system");
        assertThat(req.title()).isEqualTo("Contract Review");
    }

    @Test
    void toCreateRequest_usesOverrideTitle_whenProvided() {
        final WorkItemTemplate t = template("Default Title");
        final WorkItemCreateRequest req = WorkItemTemplateService.toCreateRequest(t, "Specific Contract #44", null, "system");
        assertThat(req.title()).isEqualTo("Specific Contract #44");
    }

    @Test
    void toCreateRequest_copiesCategory() {
        final WorkItemTemplate t = template("T");
        t.category = "legal";
        final WorkItemCreateRequest req = WorkItemTemplateService.toCreateRequest(t, null, null, "system");
        assertThat(req.category()).isEqualTo("legal");
    }

    @Test
    void toCreateRequest_copiesPriority() {
        final WorkItemTemplate t = template("T");
        t.priority = WorkItemPriority.HIGH;
        final WorkItemCreateRequest req = WorkItemTemplateService.toCreateRequest(t, null, null, "system");
        assertThat(req.priority()).isEqualTo(WorkItemPriority.HIGH);
    }

    @Test
    void toCreateRequest_copiesCandidateGroups() {
        final WorkItemTemplate t = template("T");
        t.candidateGroups = "legal-team,compliance-team";
        final WorkItemCreateRequest req = WorkItemTemplateService.toCreateRequest(t, null, null, "system");
        assertThat(req.candidateGroups()).isEqualTo("legal-team,compliance-team");
    }

    @Test
    void toCreateRequest_copiesDefaultPayload() {
        final WorkItemTemplate t = template("T");
        t.defaultPayload = "{\"type\":\"nda\"}";
        final WorkItemCreateRequest req = WorkItemTemplateService.toCreateRequest(t, null, null, "system");
        assertThat(req.payload()).isEqualTo("{\"type\":\"nda\"}");
    }

    @Test
    void toCreateRequest_setsCreatedBy() {
        final WorkItemTemplate t = template("T");
        final WorkItemCreateRequest req = WorkItemTemplateService.toCreateRequest(t, null, null, "finance-bot");
        assertThat(req.createdBy()).isEqualTo("finance-bot");
    }

    @Test
    void toCreateRequest_setsAssigneeOverride_whenProvided() {
        final WorkItemTemplate t = template("T");
        final WorkItemCreateRequest req = WorkItemTemplateService.toCreateRequest(t, null, "alice", "system");
        assertThat(req.assigneeId()).isEqualTo("alice");
    }

    @Test
    void toCreateRequest_nullFields_whenTemplateHasNoDefaults() {
        final WorkItemTemplate t = template("Minimal");
        final WorkItemCreateRequest req = WorkItemTemplateService.toCreateRequest(t, null, null, "system");
        assertThat(req.category()).isNull();
        assertThat(req.priority()).isNull();
        assertThat(req.candidateGroups()).isNull();
        assertThat(req.payload()).isNull();
        assertThat(req.assigneeId()).isNull();
    }

    // ── parseLabels ───────────────────────────────────────────────────────────

    @Test
    void parseLabels_returnsEmptyList_whenNull() {
        final WorkItemTemplate t = template("T");
        t.labelPaths = null;
        assertThat(WorkItemTemplateService.parseLabels(t)).isEmpty();
    }

    @Test
    void parseLabels_parsesJsonArray() {
        final WorkItemTemplate t = template("T");
        t.labelPaths = "[\"intake/triage\",\"priority/high\"]";
        final var labels = WorkItemTemplateService.parseLabels(t);
        assertThat(labels).hasSize(2);
        assertThat(labels).extracting(l -> l.path)
                .containsExactly("intake/triage", "priority/high");
        assertThat(labels).extracting(l -> l.persistence)
                .containsOnly(LabelPersistence.MANUAL);
    }

    @Test
    void parseLabels_handlesEmptyArray() {
        final WorkItemTemplate t = template("T");
        t.labelPaths = "[]";
        assertThat(WorkItemTemplateService.parseLabels(t)).isEmpty();
    }

    @Test
    void parseLabels_setsAppliedByToTemplate() {
        final WorkItemTemplate t = template("T");
        t.labelPaths = "[\"security/incident\"]";
        final var labels = WorkItemTemplateService.parseLabels(t);
        assertThat(labels.get(0).appliedBy).isEqualTo("template");
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private WorkItemTemplate template(final String name) {
        final WorkItemTemplate t = new WorkItemTemplate();
        t.name = name;
        t.createdBy = "admin";
        return t;
    }
}
