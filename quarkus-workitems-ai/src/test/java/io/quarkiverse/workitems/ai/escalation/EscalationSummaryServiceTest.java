package io.quarkiverse.workitems.ai.escalation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.workitems.runtime.model.WorkItem;
import io.quarkiverse.workitems.runtime.model.WorkItemStatus;
import io.quarkiverse.workitems.testing.InMemoryAuditEntryStore;
import io.quarkiverse.workitems.testing.InMemoryWorkItemStore;

class EscalationSummaryServiceTest {

    private InMemoryWorkItemStore workItemStore;
    private InMemoryAuditEntryStore auditStore;
    private ChatModel mockModel;

    @BeforeEach
    void setUp() {
        workItemStore = new InMemoryWorkItemStore();
        auditStore = new InMemoryAuditEntryStore();
        mockModel = mock(ChatModel.class);
    }

    private EscalationSummaryService service(final boolean enabled) {
        return new EscalationSummaryService(workItemStore, auditStore, mockModel, enabled, 5);
    }

    private EscalationSummaryService serviceNoModel() {
        return new EscalationSummaryService(workItemStore, auditStore, null, true, 5);
    }

    @Test
    void summarise_disabled_persistsNullSummaryWithoutCallingModel() {
        final WorkItem wi = workItem();
        workItemStore.put(wi);
        final EscalationSummaryService svc = service(false);

        final EscalationSummary result = svc.buildSummary(wi.id, "EXPIRED");

        assertThat(result.workItemId).isEqualTo(wi.id);
        assertThat(result.eventType).isEqualTo("EXPIRED");
        assertThat(result.summary).isNull();
        verify(mockModel, never()).chat(anyString());
    }

    @Test
    void summarise_noModel_persistsNullSummary() {
        final WorkItem wi = workItem();
        workItemStore.put(wi);

        final EscalationSummary result = serviceNoModel().buildSummary(wi.id, "CLAIM_EXPIRED");

        assertThat(result.eventType).isEqualTo("CLAIM_EXPIRED");
        assertThat(result.summary).isNull();
    }

    @Test
    void summarise_workItemNotFound_persistsNullSummary() {
        when(mockModel.chat(anyString())).thenReturn("summary text");
        final EscalationSummary result = service(true).buildSummary(UUID.randomUUID(), "EXPIRED");

        assertThat(result.summary).isNull();
        verify(mockModel, never()).chat(anyString());
    }

    @Test
    void summarise_modelAvailable_callsModelAndPersistsSummary() {
        final WorkItem wi = workItem();
        workItemStore.put(wi);
        when(mockModel.chat(anyString())).thenReturn("This work item expired without resolution.");

        final EscalationSummary result = service(true).buildSummary(wi.id, "EXPIRED");

        assertThat(result.summary).isEqualTo("This work item expired without resolution.");
        assertThat(result.workItemId).isEqualTo(wi.id);
        assertThat(result.eventType).isEqualTo("EXPIRED");
        verify(mockModel).chat(anyString());
    }

    @Test
    void summarise_modelThrows_persistsNullSummary() {
        final WorkItem wi = workItem();
        workItemStore.put(wi);
        when(mockModel.chat(anyString())).thenThrow(new RuntimeException("model down"));

        final EscalationSummary result = service(true).buildSummary(wi.id, "EXPIRED");

        assertThat(result.summary).isNull();
    }

    @Test
    void summarise_promptContainsEscalationReason_forExpired() {
        final WorkItem wi = workItem();
        workItemStore.put(wi);
        when(mockModel.chat(anyString())).thenAnswer(inv -> {
            final String prompt = inv.getArgument(0, String.class);
            assertThat(prompt).contains("completion deadline");
            assertThat(prompt).contains(wi.title);
            return "ok";
        });

        service(true).buildSummary(wi.id, "EXPIRED");
        verify(mockModel).chat(anyString());
    }

    @Test
    void summarise_promptContainsClaimReason_forClaimExpired() {
        final WorkItem wi = workItem();
        workItemStore.put(wi);
        when(mockModel.chat(anyString())).thenAnswer(inv -> {
            final String prompt = inv.getArgument(0, String.class);
            assertThat(prompt).contains("claim deadline");
            return "ok";
        });

        service(true).buildSummary(wi.id, "CLAIM_EXPIRED");
        verify(mockModel).chat(anyString());
    }

    private WorkItem workItem() {
        final WorkItem wi = new WorkItem();
        wi.id = UUID.randomUUID();
        wi.title = "Review quarterly contracts";
        wi.description = "Legal review of all Q2 contracts";
        wi.category = "legal";
        wi.status = WorkItemStatus.PENDING;
        wi.createdAt = Instant.now();
        wi.updatedAt = Instant.now();
        return wi;
    }
}
