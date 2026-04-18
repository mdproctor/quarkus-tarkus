package io.quarkiverse.workitems.queues.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.quarkiverse.workitems.queues.event.QueueEventType;
import io.quarkiverse.workitems.queues.event.WorkItemQueueEvent;
import io.quarkiverse.workitems.queues.model.QueueView;
import io.quarkiverse.workitems.runtime.model.LabelPersistence;
import io.quarkiverse.workitems.runtime.model.WorkItem;
import io.quarkiverse.workitems.runtime.model.WorkItemLabel;

/**
 * Pure unit tests for {@link QueueMembershipContext} — no Quarkus, no CDI, no DB.
 *
 * <p>
 * Covers the core diff logic: ADDED / REMOVED / CHANGED, including edge cases
 * (multi-queue moves, empty before/after, exact pattern matching).
 */
class QueueMembershipContextTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static final UUID WORK_ITEM_ID = UUID.randomUUID();

    private QueueView queue(final UUID id, final String name, final String pattern) {
        final QueueView qv = new QueueView();
        qv.id = id;
        qv.name = name;
        qv.labelPattern = pattern;
        return qv;
    }

    private WorkItem workItem(final String... labelPaths) {
        final WorkItem wi = new WorkItem();
        wi.id = WORK_ITEM_ID;
        wi.labels = new ArrayList<>();
        for (final String path : labelPaths) {
            wi.labels.add(new WorkItemLabel(path, LabelPersistence.MANUAL, "alice"));
        }
        return wi;
    }

    /** Runs resolve() and collects fired events into a list. */
    private List<WorkItemQueueEvent> resolve(
            final Map<UUID, String> before,
            final WorkItem wi,
            final List<QueueView> queues) {
        final List<WorkItemQueueEvent> fired = new ArrayList<>();
        new QueueMembershipContext(WORK_ITEM_ID, before).resolve(wi, queues, fired::add);
        return fired;
    }

    // ── No events ─────────────────────────────────────────────────────────────

    @Test
    void noEvents_whenBothEmpty() {
        final var events = resolve(Map.of(), workItem(), List.of());
        assertThat(events).isEmpty();
    }

    @Test
    void noEvents_whenItemHasNoLabelsAndNoQueues() {
        final UUID qId = UUID.randomUUID();
        final var before = Map.of(qId, "Legal Queue");
        // Simulate: item was in Q, then lost all labels, then re-gained same membership
        // Trick: before has Q, after has Q (same) → CHANGED fires (not empty)
        // But if both truly empty:
        final var events = resolve(Map.of(), workItem(), List.of());
        assertThat(events).isEmpty();
    }

    // ── ADDED ─────────────────────────────────────────────────────────────────

    @Test
    void added_whenItemEntersNewQueue() {
        final UUID qId = UUID.randomUUID();
        // before: not in any queue
        // after: label "legal/contracts" matches queue pattern "legal/**"
        final var events = resolve(
                Map.of(),
                workItem("legal/contracts"),
                List.of(queue(qId, "Legal Queue", "legal/**")));

        assertThat(events).hasSize(1);
        assertThat(events.get(0).eventType()).isEqualTo(QueueEventType.ADDED);
        assertThat(events.get(0).queueViewId()).isEqualTo(qId);
        assertThat(events.get(0).queueName()).isEqualTo("Legal Queue");
        assertThat(events.get(0).workItemId()).isEqualTo(WORK_ITEM_ID);
    }

    @Test
    void added_exactPatternMatch() {
        final UUID qId = UUID.randomUUID();
        final var events = resolve(
                Map.of(),
                workItem("intake"),
                List.of(queue(qId, "Intake", "intake")));

        assertThat(events).hasSize(1);
        assertThat(events.get(0).eventType()).isEqualTo(QueueEventType.ADDED);
    }

    @Test
    void added_singleWildcardPattern() {
        final UUID qId = UUID.randomUUID();
        // "legal/*" matches "legal/contracts" but not "legal/contracts/nda"
        final var events = resolve(
                Map.of(),
                workItem("legal/contracts"),
                List.of(queue(qId, "Legal Direct", "legal/*")));

        assertThat(events).hasSize(1);
        assertThat(events.get(0).eventType()).isEqualTo(QueueEventType.ADDED);
    }

    @Test
    void noAdded_whenPatternDoesNotMatchLabel() {
        final UUID qId = UUID.randomUUID();
        // "finance/**" does NOT match "legal/contracts"
        final var events = resolve(
                Map.of(),
                workItem("legal/contracts"),
                List.of(queue(qId, "Finance Queue", "finance/**")));

        assertThat(events).isEmpty();
    }

    @Test
    void added_toMultipleQueues_whenLabelMatchesAll() {
        final UUID q1 = UUID.randomUUID();
        final UUID q2 = UUID.randomUUID();
        // "legal/contracts" matches both "legal/**" and "legal/*"
        final var events = resolve(
                Map.of(),
                workItem("legal/contracts"),
                List.of(queue(q1, "Legal All", "legal/**"),
                        queue(q2, "Legal Direct", "legal/*")));

        assertThat(events).hasSize(2);
        assertThat(events).allMatch(e -> e.eventType() == QueueEventType.ADDED);
    }

    @Test
    void added_viaAnyMatchingLabel() {
        final UUID qId = UUID.randomUUID();
        // Item has two labels; the queue pattern matches the second one
        final var events = resolve(
                Map.of(),
                workItem("intake", "legal/contracts"),
                List.of(queue(qId, "Legal Queue", "legal/**")));

        assertThat(events).hasSize(1);
        assertThat(events.get(0).eventType()).isEqualTo(QueueEventType.ADDED);
    }

    // ── REMOVED ───────────────────────────────────────────────────────────────

    @Test
    void removed_whenItemLeavesQueue() {
        final UUID qId = UUID.randomUUID();
        // before: item was in Q
        // after: item has no matching label → not in Q
        final var events = resolve(
                Map.of(qId, "Legal Queue"),
                workItem(), // no labels
                List.of(queue(qId, "Legal Queue", "legal/**")));

        assertThat(events).hasSize(1);
        assertThat(events.get(0).eventType()).isEqualTo(QueueEventType.REMOVED);
        assertThat(events.get(0).queueViewId()).isEqualTo(qId);
    }

    @Test
    void removed_usesNameFromBefore_notAfter() {
        final UUID qId = UUID.randomUUID();
        // The queue name stored in the before-snapshot is used for the REMOVED event
        // (even if the QueueView was renamed in the meantime)
        final var events = resolve(
                Map.of(qId, "Old Queue Name"),
                workItem(),
                List.of(queue(qId, "New Queue Name", "legal/**")));

        assertThat(events).hasSize(1);
        assertThat(events.get(0).eventType()).isEqualTo(QueueEventType.REMOVED);
        assertThat(events.get(0).queueName()).isEqualTo("Old Queue Name");
    }

    @Test
    void removed_fromMultipleQueues() {
        final UUID q1 = UUID.randomUUID();
        final UUID q2 = UUID.randomUUID();
        final var events = resolve(
                Map.of(q1, "Q1", q2, "Q2"),
                workItem(), // no labels
                List.of(queue(q1, "Q1", "legal/**"), queue(q2, "Q2", "finance/**")));

        assertThat(events).hasSize(2);
        assertThat(events).allMatch(e -> e.eventType() == QueueEventType.REMOVED);
    }

    // ── CHANGED ───────────────────────────────────────────────────────────────

    @Test
    void changed_whenItemStaysInQueue() {
        final UUID qId = UUID.randomUUID();
        // Item was in Q, and is still in Q after re-evaluation (INFERRED strip + re-apply)
        final var events = resolve(
                Map.of(qId, "Legal Queue"),
                workItem("legal/contracts"),
                List.of(queue(qId, "Legal Queue", "legal/**")));

        assertThat(events).hasSize(1);
        assertThat(events.get(0).eventType()).isEqualTo(QueueEventType.CHANGED);
    }

    @Test
    void changed_usesNameFromAfter() {
        final UUID qId = UUID.randomUUID();
        final var events = resolve(
                Map.of(qId, "Old Name"),
                workItem("legal/contracts"),
                List.of(queue(qId, "New Name", "legal/**")));

        // CHANGED name comes from the after (current) QueueView
        assertThat(events.get(0).queueName()).isEqualTo("New Name");
    }

    // ── Mixed scenarios ───────────────────────────────────────────────────────

    @Test
    void mixed_addedAndRemoved_whenItemMovesQueues() {
        final UUID q1 = UUID.randomUUID();
        final UUID q2 = UUID.randomUUID();
        // before: in Q1, after: in Q2
        final var events = resolve(
                Map.of(q1, "Legal"),
                workItem("finance/budget"),
                List.of(queue(q1, "Legal", "legal/**"),
                        queue(q2, "Finance", "finance/**")));

        assertThat(events).hasSize(2);
        assertThat(events).anyMatch(e -> e.eventType() == QueueEventType.REMOVED && e.queueViewId().equals(q1));
        assertThat(events).anyMatch(e -> e.eventType() == QueueEventType.ADDED && e.queueViewId().equals(q2));
    }

    @Test
    void mixed_fullDiff_threeQueues() {
        final UUID q1 = UUID.randomUUID(); // removed
        final UUID q2 = UUID.randomUUID(); // changed (stays)
        final UUID q3 = UUID.randomUUID(); // added
        // Item has labels matching q2 and q3, but no longer q1
        final var events = resolve(
                Map.of(q1, "Finance", q2, "Legal"),
                workItem("legal/contracts", "intake"),
                List.of(
                        queue(q1, "Finance", "finance/**"),
                        queue(q2, "Legal", "legal/**"),
                        queue(q3, "Intake", "intake")));

        assertThat(events).hasSize(3);
        assertThat(events).anyMatch(e -> e.eventType() == QueueEventType.REMOVED && e.queueViewId().equals(q1));
        assertThat(events).anyMatch(e -> e.eventType() == QueueEventType.CHANGED && e.queueViewId().equals(q2));
        assertThat(events).anyMatch(e -> e.eventType() == QueueEventType.ADDED && e.queueViewId().equals(q3));
    }

    @Test
    void noSpuriousRemoved_whenQueueNoLongerExists() {
        // If a QueueView was deleted (not in allQueues anymore), and item was "in" it before,
        // that queue does NOT appear in "after" → REMOVED fires (correct: item left the queue
        // because the queue itself was deleted or no longer matches)
        final UUID deletedQueueId = UUID.randomUUID();
        final var events = resolve(
                Map.of(deletedQueueId, "Deleted Queue"),
                workItem("legal/contracts"),
                List.of()); // no queues — the queue was deleted

        assertThat(events).hasSize(1);
        assertThat(events.get(0).eventType()).isEqualTo(QueueEventType.REMOVED);
    }
}
