package io.quarkiverse.work.runtime.multiinstance;

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

import io.quarkiverse.work.api.GroupStatus;
import io.quarkiverse.work.api.WorkItemGroupLifecycleEvent;
import io.quarkiverse.work.runtime.model.WorkItem;
import io.quarkiverse.work.runtime.model.WorkItemTemplate;
import io.quarkiverse.work.runtime.service.WorkItemService;
import io.quarkiverse.work.runtime.service.WorkItemTemplateService;
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
        UUID parentId = inTx(() -> {
            WorkItemTemplate t = new WorkItemTemplate();
            t.name = "EventTest";
            t.candidateGroups = "g";
            t.createdBy = "test";
            t.instanceCount = 3;
            t.requiredCount = 2;
            t.persist();
            return templateService.instantiate(t, null, null, "test").id;
        });

        List<UUID> children = inTx(() -> WorkItem.<WorkItem> list("parentId", parentId).stream().map(w -> w.id).toList());

        inTx(() -> workItemService.claim(children.get(0), "a"));
        inTx(() -> workItemService.start(children.get(0), "a"));
        inTx(() -> workItemService.complete(children.get(0), "a", "ok"));

        Awaitility.await().atMost(Duration.ofSeconds(5))
                .until(() -> capture.hasStatus(GroupStatus.IN_PROGRESS));

        assertThat(capture.byStatus(GroupStatus.IN_PROGRESS)).hasSize(1);
        assertThat(capture.byStatus(GroupStatus.IN_PROGRESS).get(0).completedCount()).isEqualTo(1);
    }

    @Test
    void completedEventFiresExactlyOnceAtThreshold() {
        UUID parentId = inTx(() -> {
            WorkItemTemplate t = new WorkItemTemplate();
            t.name = "CompletedEventTest";
            t.candidateGroups = "g";
            t.createdBy = "test";
            t.instanceCount = 3;
            t.requiredCount = 2;
            t.persist();
            return templateService.instantiate(t, null, null, "test").id;
        });

        List<UUID> children = inTx(() -> WorkItem.<WorkItem> list("parentId", parentId).stream().map(w -> w.id).toList());

        for (UUID c : children) {
            inTx(() -> workItemService.claim(c, "a"));
            inTx(() -> workItemService.start(c, "a"));
            inTx(() -> workItemService.complete(c, "a", "ok"));
        }

        Awaitility.await().atMost(Duration.ofSeconds(5))
                .until(() -> capture.hasStatus(GroupStatus.COMPLETED));

        assertThat(capture.byStatus(GroupStatus.COMPLETED)).hasSize(1);
    }

    @Transactional
    <T> T inTx(Supplier<T> s) {
        return s.get();
    }

    @Transactional
    void inTx(Runnable r) {
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

        boolean hasStatus(GroupStatus s) {
            synchronized (events) {
                return events.stream().anyMatch(e -> e.groupStatus() == s);
            }
        }

        List<WorkItemGroupLifecycleEvent> byStatus(GroupStatus s) {
            synchronized (events) {
                return events.stream().filter(e -> e.groupStatus() == s).toList();
            }
        }
    }
}
