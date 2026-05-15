package io.casehub.work.runtime.multiinstance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.work.api.GroupStatus;
import io.casehub.work.api.WorkItemGroupLifecycleEvent;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemTemplate;
import io.casehub.work.runtime.service.WorkItemService;
import io.casehub.work.runtime.service.WorkItemTemplateService;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class WorkItemGroupLifecycleEventTest {

    @Inject
    WorkItemTemplateService templateService;

    @Inject
    WorkItemService workItemService;

    @Inject
    EventCapture capture;

    @BeforeEach
    void clearCapture() {
        capture.clear();
    }

    @Test
    void inProgressEventFiresOnFirstChildTerminalBeforeThreshold() {
        final UUID parentId = inTx(() -> {
            WorkItemTemplate t = new WorkItemTemplate();
            t.name = "EventTest";
            t.candidateGroups = "g";
            t.createdBy = "test";
            t.instanceCount = 3;
            t.requiredCount = 2;
            t.persist();
            return templateService.instantiate(t, null, null, "test").id;
        });

        final List<UUID> children = inTx(() ->
                WorkItem.<WorkItem>list("parentId", parentId).stream().map(w -> w.id).toList());

        inTx(() -> workItemService.claim(children.get(0), "a"));
        inTx(() -> workItemService.start(children.get(0), "a"));
        inTx(() -> workItemService.complete(children.get(0), "a", "ok"));

        // Filter by this test's parentId — immune to events leaking from other tests
        // via @ObservesAsync threads that arrive after @BeforeEach clear().
        Awaitility.await().atMost(Duration.ofSeconds(5))
                .until(() -> capture.hasStatus(parentId, GroupStatus.IN_PROGRESS));

        assertThat(capture.byStatus(parentId, GroupStatus.IN_PROGRESS)).hasSize(1);
        assertThat(capture.byStatus(parentId, GroupStatus.IN_PROGRESS).get(0).completedCount())
                .isEqualTo(1);
    }

    @Test
    void completedEventFiresExactlyOnceAtThreshold() {
        final UUID parentId = inTx(() -> {
            WorkItemTemplate t = new WorkItemTemplate();
            t.name = "CompletedEventTest";
            t.candidateGroups = "g";
            t.createdBy = "test";
            t.instanceCount = 3;
            t.requiredCount = 2;
            t.persist();
            return templateService.instantiate(t, null, null, "test").id;
        });

        final List<UUID> children = inTx(() ->
                WorkItem.<WorkItem>list("parentId", parentId).stream().map(w -> w.id).toList());

        // Complete only requiredCount (2) children — not all 3.
        // onThresholdReached is not set here so it defaults to KEEP (no side effects).
        // The threshold fires on requiredCount completions; completing the surplus child
        // is unnecessary. If CANCEL were set, completing the surplus would race with
        // the coordinator's async cancel — see MultiInstanceCoordinatorTest for that scenario.
        for (final UUID c : children.subList(0, 2)) {
            inTx(() -> workItemService.claim(c, "a"));
            inTx(() -> workItemService.start(c, "a"));
            inTx(() -> workItemService.complete(c, "a", "ok"));
        }

        // Wait until COMPLETED appears AND stays at exactly 1 for a stability window.
        // The stability window catches any duplicate events from concurrent coordinator
        // invocations that arrive within milliseconds of the first COMPLETED event.
        // parentId filter eliminates contamination from other tests' async events.
        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .during(Duration.ofMillis(300))
                .until(() -> capture.byStatus(parentId, GroupStatus.COMPLETED).size() == 1);
    }

    @Test
    void completedGroupEvent_carriesCallerRef() {
        final String callerRef = "case:550e8400-e29b-41d4-a716-446655440000/pi:ethics-gate";

        final UUID parentId = inTx(() -> {
            WorkItemTemplate t = new WorkItemTemplate();
            t.name = "CallerRefEventTest";
            t.candidateGroups = "g";
            t.createdBy = "test";
            t.instanceCount = 2;
            t.requiredCount = 2;
            t.persist();
            return templateService.instantiate(t, null, null, "test", callerRef).id;
        });

        final List<UUID> childIds = inTx(() ->
            WorkItem.<WorkItem>list("parentId", parentId).stream()
                .map(w -> w.id).toList());

        inTx(() -> workItemService.claim(childIds.get(0), "alice"));
        inTx(() -> workItemService.start(childIds.get(0), "alice"));
        inTx(() -> workItemService.complete(childIds.get(0), "alice", "approved"));

        inTx(() -> workItemService.claim(childIds.get(1), "bob"));
        inTx(() -> workItemService.start(childIds.get(1), "bob"));
        inTx(() -> workItemService.complete(childIds.get(1), "bob", "approved"));

        // Stability window guards the count — same pattern as completedEventFiresExactlyOnceAtThreshold.
        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .during(Duration.ofMillis(300))
                .until(() -> capture.byStatus(parentId, GroupStatus.COMPLETED).size() == 1);

        assertThat(capture.byStatus(parentId, GroupStatus.COMPLETED).get(0).callerRef())
                .isEqualTo(callerRef);
    }

    @Transactional
    <T> T inTx(final Supplier<T> s) {
        return s.get();
    }

    @Transactional
    void inTx(final Runnable r) {
        r.run();
    }

    @ApplicationScoped
    static class EventCapture {

        private final List<WorkItemGroupLifecycleEvent> events = new ArrayList<>();

        void onEvent(@ObservesAsync WorkItemGroupLifecycleEvent event) {
            synchronized (events) {
                events.add(event);
            }
        }

        void clear() {
            synchronized (events) {
                events.clear();
            }
        }

        boolean hasStatus(final UUID parentId, final GroupStatus s) {
            synchronized (events) {
                return events.stream()
                        .anyMatch(e -> e.groupStatus() == s && parentId.equals(e.parentId()));
            }
        }

        List<WorkItemGroupLifecycleEvent> byStatus(final UUID parentId, final GroupStatus s) {
            synchronized (events) {
                return events.stream()
                        .filter(e -> e.groupStatus() == s && parentId.equals(e.parentId()))
                        .toList();
            }
        }
    }
}
