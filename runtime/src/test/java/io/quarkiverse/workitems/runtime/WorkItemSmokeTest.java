package io.quarkiverse.workitems.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkiverse.workitems.runtime.model.AuditEntry;
import io.quarkiverse.workitems.runtime.model.WorkItem;
import io.quarkiverse.workitems.runtime.model.WorkItemCreateRequest;
import io.quarkiverse.workitems.runtime.model.WorkItemPriority;
import io.quarkiverse.workitems.runtime.model.WorkItemStatus;
import io.quarkiverse.workitems.runtime.repository.AuditEntryRepository;
import io.quarkiverse.workitems.runtime.repository.WorkItemRepository;
import io.quarkiverse.workitems.runtime.service.WorkItemNotFoundException;
import io.quarkiverse.workitems.runtime.service.WorkItemService;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@TestTransaction
class WorkItemSmokeTest {

    @Inject
    WorkItemService service;

    @Inject
    AuditEntryRepository auditRepo;

    @Inject
    WorkItemRepository workItemRepo;

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private WorkItemCreateRequest basicRequest() {
        return new WorkItemCreateRequest(
                "Test item", "Do something", null, null,
                WorkItemPriority.NORMAL,
                null, null, null, null,
                "system", null, null, null, null);
    }

    // -------------------------------------------------------------------------
    // Scenarios
    // -------------------------------------------------------------------------

    @Test
    void createAndStatus() {
        WorkItem wi = service.create(basicRequest());
        assertThat(wi.status).isEqualTo(WorkItemStatus.PENDING);
    }

    @Test
    void fullHappyPath() {
        WorkItem wi = service.create(basicRequest());
        service.claim(wi.id, "alice");
        service.start(wi.id, "alice");
        wi = service.complete(wi.id, "alice", "All done");

        assertThat(wi.status).isEqualTo(WorkItemStatus.COMPLETED);
        assertThat(wi.resolution).isEqualTo("All done");
    }

    @Test
    void rejectPath() {
        WorkItem wi = service.create(basicRequest());
        service.claim(wi.id, "alice");
        wi = service.reject(wi.id, "alice", "Not applicable");

        assertThat(wi.status).isEqualTo(WorkItemStatus.REJECTED);
    }

    @Test
    void delegatePath() {
        WorkItem wi = service.create(basicRequest());
        service.claim(wi.id, "alice");
        wi = service.delegate(wi.id, "alice", "bob");

        assertThat(wi.status).isEqualTo(WorkItemStatus.PENDING);
        assertThat(wi.assigneeId).isEqualTo("bob");
    }

    @Test
    void releasePath() {
        WorkItem wi = service.create(basicRequest());
        service.claim(wi.id, "alice");
        wi = service.release(wi.id, "alice");

        assertThat(wi.status).isEqualTo(WorkItemStatus.PENDING);
        assertThat(wi.assigneeId).isNull();
    }

    @Test
    void suspendResumePath() {
        WorkItem wi = service.create(basicRequest());
        service.claim(wi.id, "alice");
        service.start(wi.id, "alice");
        service.suspend(wi.id, "alice", "blocked");
        wi = service.resume(wi.id, "alice");

        assertThat(wi.status).isEqualTo(WorkItemStatus.IN_PROGRESS);
    }

    @Test
    void cancelPath() {
        WorkItem wi = service.create(basicRequest());
        wi = service.cancel(wi.id, "admin", "no longer needed");

        assertThat(wi.status).isEqualTo(WorkItemStatus.CANCELLED);
    }

    @Test
    void auditTrailGrowsWithEachOperation() {
        WorkItem wi = service.create(basicRequest());
        service.claim(wi.id, "alice");
        service.start(wi.id, "alice");
        service.complete(wi.id, "alice", "done");

        List<AuditEntry> trail = auditRepo.findByWorkItemId(wi.id);
        assertThat(trail).hasSize(4);
    }

    @Test
    void invalidTransitionThrows() {
        WorkItem wi = service.create(basicRequest());
        assertThatThrownBy(() -> service.complete(wi.id, "alice", "done"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void notFoundThrows() {
        UUID randomId = UUID.randomUUID();
        assertThatThrownBy(() -> service.claim(randomId, "alice"))
                .isInstanceOf(WorkItemNotFoundException.class);
    }

    @Test
    void defaultExpiryApplied() {
        WorkItemCreateRequest req = new WorkItemCreateRequest(
                "Test item", "Do something", null, null,
                WorkItemPriority.NORMAL,
                null, null, null, null,
                "system", null, null, null, null);
        WorkItem wi = service.create(req);
        assertThat(wi.expiresAt).isNotNull();
    }

    // -------------------------------------------------------------------------
    // Gap-filling: repository query smoke tests
    // -------------------------------------------------------------------------

    @Test
    void findExpiredQuery_returnsExpiredItem() {
        WorkItem wi = new WorkItem();
        wi.title = "Expired item";
        wi.status = WorkItemStatus.PENDING;
        wi.priority = WorkItemPriority.NORMAL;
        wi.createdAt = Instant.now();
        wi.updatedAt = Instant.now();
        wi.expiresAt = Instant.now().minus(2, ChronoUnit.HOURS);
        workItemRepo.save(wi);

        List<WorkItem> expired = workItemRepo.findExpired(Instant.now());
        assertThat(expired).extracting(w -> w.id).contains(wi.id);
    }

    @Test
    void findExpiredQuery_doesNotReturnActiveItem() {
        WorkItem wi = service.create(basicRequest()); // expiresAt = now + 24h
        List<WorkItem> expired = workItemRepo.findExpired(Instant.now());
        assertThat(expired).extracting(w -> w.id).doesNotContain(wi.id);
    }

    @Test
    void findUnclaimedPastDeadlineQuery_returnsPastDeadlinePendingItem() {
        WorkItem wi = new WorkItem();
        wi.title = "Past deadline";
        wi.status = WorkItemStatus.PENDING;
        wi.priority = WorkItemPriority.NORMAL;
        wi.createdAt = Instant.now();
        wi.updatedAt = Instant.now();
        wi.claimDeadline = Instant.now().minus(1, ChronoUnit.HOURS);
        workItemRepo.save(wi);

        List<WorkItem> unclaimed = workItemRepo.findUnclaimedPastDeadline(Instant.now());
        assertThat(unclaimed).extracting(w -> w.id).contains(wi.id);
    }

    @Test
    void claimDeadlineStoredAndRetrievable() {
        Instant deadline = Instant.now().plus(2, ChronoUnit.HOURS)
                .truncatedTo(ChronoUnit.SECONDS);
        WorkItemCreateRequest req = new WorkItemCreateRequest(
                "Test", null, null, null, WorkItemPriority.NORMAL,
                null, null, null, null, "system", null,
                deadline, null, null);
        WorkItem wi = service.create(req);
        assertThat(wi.claimDeadline).isNotNull();
        // verify it round-trips through JPA
        WorkItem reloaded = workItemRepo.findById(wi.id).orElseThrow();
        assertThat(reloaded.claimDeadline.truncatedTo(ChronoUnit.SECONDS))
                .isEqualTo(deadline);
    }
}
