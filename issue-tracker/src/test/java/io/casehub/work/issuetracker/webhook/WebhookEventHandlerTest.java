package io.casehub.work.issuetracker.webhook;

import io.casehub.work.api.NormativeResolution;
import io.casehub.work.issuetracker.model.WorkItemIssueLink;
import io.casehub.work.issuetracker.repository.IssueLinkStore;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemLabel;
import io.casehub.work.runtime.model.WorkItemPriority;
import io.casehub.work.runtime.model.WorkItemStatus;
import io.casehub.work.runtime.repository.WorkItemStore;
import io.casehub.work.runtime.service.WorkItemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class WebhookEventHandlerTest {

    private IssueLinkStore linkStore;
    private WorkItemStore workItemStore;
    private WorkItemService workItemService;
    private WebhookEventHandler handler;
    private UUID workItemId;

    @BeforeEach
    void setUp() {
        linkStore = mock(IssueLinkStore.class);
        workItemStore = mock(WorkItemStore.class);
        workItemService = mock(WorkItemService.class);
        handler = new WebhookEventHandler(linkStore, workItemStore, workItemService);
        workItemId = UUID.randomUUID();
    }

    private WorkItem activeWorkItem() {
        WorkItem wi = new WorkItem();
        wi.id = workItemId;
        wi.status = WorkItemStatus.ASSIGNED;
        wi.assigneeId = "alice";
        wi.labels = new ArrayList<>();
        return wi;
    }

    private WorkItemIssueLink linkFor(final UUID wid) {
        final WorkItemIssueLink link = new WorkItemIssueLink();
        link.workItemId = wid;
        return link;
    }

    // ── handle(WebhookEvent) — public entry point ─────────────────────────────

    @Test
    void handle_singleLink_workItemFound_appliesTransition() {
        final WorkItem wi = activeWorkItem();
        when(linkStore.findByTrackerRef("github", "owner/repo#42"))
                .thenReturn(List.of(linkFor(workItemId)));
        when(workItemStore.get(workItemId)).thenReturn(Optional.of(wi));

        handler.handle(new WebhookEvent(
                "github", "owner/repo#42", WebhookEventKind.CLOSED,
                "alice", NormativeResolution.DONE, null, null, null, null, null));

        verify(workItemService).complete(workItemId, "alice", null);
    }

    @Test
    void handle_noLinksFound_noOp() {
        when(linkStore.findByTrackerRef("github", "owner/repo#99"))
                .thenReturn(List.of());

        handler.handle(new WebhookEvent(
                "github", "owner/repo#99", WebhookEventKind.CLOSED,
                "alice", NormativeResolution.DONE, null, null, null, null, null));

        verifyNoInteractions(workItemService);
    }

    @Test
    void handle_workItemNotFound_skipsGracefully() {
        when(linkStore.findByTrackerRef("github", "owner/repo#42"))
                .thenReturn(List.of(linkFor(workItemId)));
        when(workItemStore.get(workItemId)).thenReturn(Optional.empty());

        handler.handle(new WebhookEvent(
                "github", "owner/repo#42", WebhookEventKind.CLOSED,
                "alice", NormativeResolution.DONE, null, null, null, null, null));

        verifyNoInteractions(workItemService);
    }

    @Test
    void handle_multipleLinks_allGetTransition() {
        final UUID wid1 = UUID.randomUUID();
        final UUID wid2 = UUID.randomUUID();

        final WorkItem wi1 = new WorkItem();
        wi1.id = wid1;
        wi1.status = WorkItemStatus.ASSIGNED;
        wi1.labels = new ArrayList<>();

        final WorkItem wi2 = new WorkItem();
        wi2.id = wid2;
        wi2.status = WorkItemStatus.IN_PROGRESS;
        wi2.labels = new ArrayList<>();

        when(linkStore.findByTrackerRef("github", "owner/repo#42"))
                .thenReturn(List.of(linkFor(wid1), linkFor(wid2)));
        when(workItemStore.get(wid1)).thenReturn(Optional.of(wi1));
        when(workItemStore.get(wid2)).thenReturn(Optional.of(wi2));

        handler.handle(new WebhookEvent(
                "github", "owner/repo#42", WebhookEventKind.CLOSED,
                "alice", NormativeResolution.DONE, null, null, null, null, null));

        verify(workItemService).complete(wid1, "alice", null);
        verify(workItemService).complete(wid2, "alice", null);
    }

    @Test
    void handle_firstLinkThrows_secondStillProcessed() {
        final UUID wid1 = UUID.randomUUID();
        final UUID wid2 = UUID.randomUUID();

        final WorkItem wi1 = new WorkItem();
        wi1.id = wid1;
        wi1.status = WorkItemStatus.ASSIGNED;
        wi1.labels = new ArrayList<>();

        final WorkItem wi2 = new WorkItem();
        wi2.id = wid2;
        wi2.status = WorkItemStatus.ASSIGNED;
        wi2.labels = new ArrayList<>();

        when(linkStore.findByTrackerRef("github", "owner/repo#42"))
                .thenReturn(List.of(linkFor(wid1), linkFor(wid2)));
        when(workItemStore.get(wid1)).thenReturn(Optional.of(wi1));
        when(workItemStore.get(wid2)).thenReturn(Optional.of(wi2));
        doThrow(new RuntimeException("service error"))
                .when(workItemService).complete(eq(wid1), any(), any());

        assertThatNoException().isThrownBy(() -> handler.handle(new WebhookEvent(
                "github", "owner/repo#42", WebhookEventKind.CLOSED,
                "alice", NormativeResolution.DONE, null, null, null, null, null)));

        verify(workItemService).complete(wid2, "alice", null);
    }

    // ── applyTransition / handle(UUID, WorkItem, WebhookEvent) ───────────────

    @Test
    void closed_done_callsComplete() {
        handler.applyTransition(workItemId, activeWorkItem(), new WebhookEvent(
                "github", "owner/repo#42", WebhookEventKind.CLOSED,
                "alice", NormativeResolution.DONE, null, null, null, null, null));

        verify(workItemService).complete(workItemId, "alice", null);
    }

    @Test
    void closed_decline_callsCancel() {
        handler.applyTransition(workItemId, activeWorkItem(), new WebhookEvent(
                "github", "owner/repo#42", WebhookEventKind.CLOSED,
                "alice", NormativeResolution.DECLINE, null, null, null, null, null));

        verify(workItemService).cancel(workItemId, "alice", null);
    }

    @Test
    void closed_failure_callsReject() {
        handler.applyTransition(workItemId, activeWorkItem(), new WebhookEvent(
                "github", "owner/repo#42", WebhookEventKind.CLOSED,
                "alice", NormativeResolution.FAILURE, null, null, null, null, null));

        verify(workItemService).reject(workItemId, "alice", null);
    }

    @Test
    void assigned_pending_callsClaim() {
        WorkItem wi = new WorkItem();
        wi.id = workItemId;
        wi.status = WorkItemStatus.PENDING;
        wi.labels = new ArrayList<>();

        handler.applyTransition(workItemId, wi, new WebhookEvent(
                "github", "owner/repo#42", WebhookEventKind.ASSIGNED,
                "alice", null, null, null, null, null, "bob"));

        verify(workItemService).claim(workItemId, "bob");
    }

    @Test
    void assigned_alreadyAssigned_updatesAssigneeDirectly() {
        WorkItem wi = activeWorkItem();

        handler.applyTransition(workItemId, wi, new WebhookEvent(
                "github", "owner/repo#42", WebhookEventKind.ASSIGNED,
                "alice", null, null, null, null, null, "carol"));

        verify(workItemService, never()).claim(any(), any());
        assertThat(wi.assigneeId).isEqualTo("carol");
    }

    @Test
    void unassigned_callsRelease() {
        handler.applyTransition(workItemId, activeWorkItem(), new WebhookEvent(
                "github", "owner/repo#42", WebhookEventKind.UNASSIGNED,
                "alice", null, null, null, null, null, null));

        verify(workItemService).release(workItemId, "alice");
    }

    @Test
    void titleChanged_updatesTitle() {
        WorkItem wi = activeWorkItem();

        handler.applyTransition(workItemId, wi, new WebhookEvent(
                "github", "owner/repo#42", WebhookEventKind.TITLE_CHANGED,
                "alice", null, null, null, "New Title", null, null));

        assertThat(wi.title).isEqualTo("New Title");
        verifyNoMoreInteractions(workItemService);
    }

    @Test
    void descriptionChanged_stripsFooter() {
        WorkItem wi = activeWorkItem();
        String rawBody = "The real description.\n\n---\n*Linked WorkItem: `" + workItemId + "`*";

        handler.applyTransition(workItemId, wi, new WebhookEvent(
                "github", "owner/repo#42", WebhookEventKind.DESCRIPTION_CHANGED,
                "alice", null, null, null, null, rawBody, null));

        assertThat(wi.description).isEqualTo("The real description.");
    }

    @Test
    void priorityChanged_updatesPriority() {
        WorkItem wi = activeWorkItem();

        handler.applyTransition(workItemId, wi, new WebhookEvent(
                "github", "owner/repo#42", WebhookEventKind.PRIORITY_CHANGED,
                "alice", null, WorkItemPriority.HIGH, null, null, null, null));

        assertThat(wi.priority).isEqualTo(WorkItemPriority.HIGH);
    }

    @Test
    void labelAdded_appendsLabel() {
        WorkItem wi = activeWorkItem();

        handler.applyTransition(workItemId, wi, new WebhookEvent(
                "github", "owner/repo#42", WebhookEventKind.LABEL_ADDED,
                "alice", null, null, "legal/nda", null, null, null));

        assertThat(wi.labels).anyMatch(l -> "legal/nda".equals(l.path));
    }

    @Test
    void labelAdded_duplicate_notAddedTwice() {
        WorkItem wi = activeWorkItem();
        WorkItemLabel existing = new WorkItemLabel();
        existing.path = "legal/nda";
        wi.labels.add(existing);

        handler.applyTransition(workItemId, wi, new WebhookEvent(
                "github", "owner/repo#42", WebhookEventKind.LABEL_ADDED,
                "alice", null, null, "legal/nda", null, null, null));

        assertThat(wi.labels).hasSize(1);
    }

    @Test
    void labelRemoved_removesLabel() {
        WorkItem wi = activeWorkItem();
        WorkItemLabel label = new WorkItemLabel();
        label.path = "legal/nda";
        wi.labels.add(label);

        handler.applyTransition(workItemId, wi, new WebhookEvent(
                "github", "owner/repo#42", WebhookEventKind.LABEL_REMOVED,
                "alice", null, null, "legal/nda", null, null, null));

        assertThat(wi.labels).isEmpty();
    }

    @Test
    void terminalWorkItem_skippedSilently() {
        WorkItem wi = new WorkItem();
        wi.id = workItemId;
        wi.status = WorkItemStatus.COMPLETED;

        handler.handle(workItemId, wi, new WebhookEvent(
                "github", "owner/repo#42", WebhookEventKind.CLOSED,
                "alice", NormativeResolution.DONE, null, null, null, null, null));

        verifyNoInteractions(workItemService);
    }

    @Test
    void transitionFailure_swallowed() {
        doThrow(new IllegalStateException("wrong status"))
                .when(workItemService).complete(any(), any(), any());

        assertThatNoException().isThrownBy(() -> handler.handle(workItemId, activeWorkItem(),
                new WebhookEvent("github", "owner/repo#42", WebhookEventKind.CLOSED,
                        "alice", NormativeResolution.DONE, null, null, null, null, null)));
    }
}
