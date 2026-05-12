package io.casehub.work.postgres.broadcaster;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.casehub.work.runtime.event.WorkItemLifecycleEvent;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemStatus;

/**
 * Correctness tests for {@link WorkItemEventPayload} — round-trip fidelity and field mapping.
 */
class WorkItemEventPayloadTest {

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void from_capturesAllScalarFields() {
        final WorkItemLifecycleEvent event = sampleEvent("CREATED");

        final WorkItemEventPayload payload = WorkItemEventPayload.from(event);

        assertThat(payload.type()).isEqualTo(event.type());
        assertThat(payload.source()).isEqualTo(event.sourceUri());
        assertThat(payload.subject()).isEqualTo(event.subject());
        assertThat(payload.workItemId()).isEqualTo(event.workItemId());
        assertThat(payload.status()).isEqualTo(event.status());
        assertThat(payload.occurredAt()).isEqualTo(event.occurredAt());
        assertThat(payload.actor()).isEqualTo(event.actor());
        assertThat(payload.detail()).isEqualTo(event.detail());
        assertThat(payload.rationale()).isEqualTo(event.rationale());
        assertThat(payload.planRef()).isEqualTo(event.planRef());
    }

    @Test
    void toEvent_roundTripsAllFields() {
        final WorkItemLifecycleEvent original = sampleEvent("COMPLETED");
        final WorkItemEventPayload payload = WorkItemEventPayload.from(original);

        final WorkItemLifecycleEvent reconstructed = payload.toEvent();

        assertThat(reconstructed.type()).isEqualTo(original.type());
        assertThat(reconstructed.sourceUri()).isEqualTo(original.sourceUri());
        assertThat(reconstructed.subject()).isEqualTo(original.subject());
        assertThat(reconstructed.workItemId()).isEqualTo(original.workItemId());
        assertThat(reconstructed.status()).isEqualTo(original.status());
        assertThat(reconstructed.occurredAt()).isEqualTo(original.occurredAt());
        assertThat(reconstructed.actor()).isEqualTo(original.actor());
        assertThat(reconstructed.detail()).isEqualTo(original.detail());
        assertThat(reconstructed.rationale()).isEqualTo(original.rationale());
        assertThat(reconstructed.planRef()).isEqualTo(original.planRef());
    }

    @Test
    void toEvent_nullableFieldsPreserved() {
        final WorkItemLifecycleEvent event = sampleEvent("ASSIGNED");
        final WorkItemEventPayload payload = WorkItemEventPayload.from(event);

        final WorkItemLifecycleEvent reconstructed = payload.toEvent();

        assertThat(reconstructed.detail()).isNull();
        assertThat(reconstructed.rationale()).isNull();
        assertThat(reconstructed.planRef()).isNull();
    }

    @Test
    void from_withRationaleAndPlanRef_preservesThem() {
        final WorkItem wi = workItem(UUID.randomUUID());
        final WorkItemLifecycleEvent event = WorkItemLifecycleEvent.of(
                "COMPLETED", wi, "alice", null, "income verified", "policy-v2");

        final WorkItemEventPayload payload = WorkItemEventPayload.from(event);

        assertThat(payload.rationale()).isEqualTo("income verified");
        assertThat(payload.planRef()).isEqualTo("policy-v2");
    }

    // ── Correctness ───────────────────────────────────────────────────────────

    @Test
    void from_doesNotCaptureWorkItemEntity() {
        final WorkItemLifecycleEvent event = sampleEvent("CREATED");
        final WorkItemEventPayload payload = WorkItemEventPayload.from(event);

        // payload has no WorkItem — the toEvent() result returns null from source()
        assertThat(payload.toEvent().source()).isNull();
    }

    @Test
    void typePrefix_correctlyForwarded() {
        final WorkItemLifecycleEvent event = sampleEvent("REJECTED");
        final WorkItemEventPayload payload = WorkItemEventPayload.from(event);

        assertThat(payload.type()).startsWith("io.casehub.work.workitem.");
        assertThat(payload.type()).endsWith("rejected");
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private WorkItemLifecycleEvent sampleEvent(final String name) {
        return WorkItemLifecycleEvent.of(name, workItem(UUID.randomUUID()), "system", null);
    }

    private WorkItem workItem(final UUID id) {
        final WorkItem wi = new WorkItem();
        wi.id = id;
        wi.status = WorkItemStatus.PENDING;
        return wi;
    }
}
