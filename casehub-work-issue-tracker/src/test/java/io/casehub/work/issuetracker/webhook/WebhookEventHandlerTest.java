package io.casehub.work.issuetracker.webhook;

import io.casehub.work.api.NormativeResolution;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemLabel;
import io.casehub.work.runtime.model.WorkItemPriority;
import io.casehub.work.runtime.model.WorkItemStatus;
import io.casehub.work.runtime.service.WorkItemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WebhookEventHandlerTest {

    private WorkItemService workItemService;
    private WebhookEventHandler handler;
    private UUID workItemId;

    @BeforeEach
    void setUp() {
        workItemService = mock(WorkItemService.class);
        handler = new WebhookEventHandler(workItemService);
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

    @Test
    void closed_done_callsComplete() {
        WebhookEvent event = new WebhookEvent(
                "github", "owner/repo#42", WebhookEventKind.CLOSED,
                "alice", NormativeResolution.DONE, null, null, null, null, null);

        handler.applyTransition(workItemId, activeWorkItem(), event);

        verify(workItemService).complete(workItemId, "alice", null);
    }

    @Test
    void closed_decline_callsCancel() {
        WebhookEvent event = new WebhookEvent(
                "github", "owner/repo#42", WebhookEventKind.CLOSED,
                "alice", NormativeResolution.DECLINE, null, null, null, null, null);

        handler.applyTransition(workItemId, activeWorkItem(), event);

        verify(workItemService).cancel(workItemId, "alice", null);
    }

    @Test
    void closed_failure_callsReject() {
        WebhookEvent event = new WebhookEvent(
                "github", "owner/repo#42", WebhookEventKind.CLOSED,
                "alice", NormativeResolution.FAILURE, null, null, null, null, null);

        handler.applyTransition(workItemId, activeWorkItem(), event);

        verify(workItemService).reject(workItemId, "alice", null);
    }

    @Test
    void assigned_pending_callsClaim() {
        WorkItem wi = new WorkItem();
        wi.id = workItemId;
        wi.status = WorkItemStatus.PENDING;
        wi.labels = new ArrayList<>();

        WebhookEvent event = new WebhookEvent(
                "github", "owner/repo#42", WebhookEventKind.ASSIGNED,
                "alice", null, null, null, null, null, "bob");

        handler.applyTransition(workItemId, wi, event);

        verify(workItemService).claim(workItemId, "bob");
    }

    @Test
    void assigned_alreadyAssigned_updatesAssigneeDirectly() {
        WorkItem wi = activeWorkItem(); // assigneeId = "alice"

        WebhookEvent event = new WebhookEvent(
                "github", "owner/repo#42", WebhookEventKind.ASSIGNED,
                "alice", null, null, null, null, null, "carol");

        handler.applyTransition(workItemId, wi, event);

        verify(workItemService, never()).claim(any(), any());
        assertThat(wi.assigneeId).isEqualTo("carol");
    }

    @Test
    void unassigned_callsRelease() {
        WebhookEvent event = new WebhookEvent(
                "github", "owner/repo#42", WebhookEventKind.UNASSIGNED,
                "alice", null, null, null, null, null, null);

        handler.applyTransition(workItemId, activeWorkItem(), event);

        verify(workItemService).release(workItemId, "alice");
    }

    @Test
    void titleChanged_updatesTitle() {
        WorkItem wi = activeWorkItem();
        WebhookEvent event = new WebhookEvent(
                "github", "owner/repo#42", WebhookEventKind.TITLE_CHANGED,
                "alice", null, null, null, "New Title", null, null);

        handler.applyTransition(workItemId, wi, event);

        assertThat(wi.title).isEqualTo("New Title");
        verifyNoMoreInteractions(workItemService);
    }

    @Test
    void descriptionChanged_stripsFooter() {
        WorkItem wi = activeWorkItem();
        String rawBody = "The real description.\n\n---\n*Linked WorkItem: `" + workItemId + "`*";
        WebhookEvent event = new WebhookEvent(
                "github", "owner/repo#42", WebhookEventKind.DESCRIPTION_CHANGED,
                "alice", null, null, null, null, rawBody, null);

        handler.applyTransition(workItemId, wi, event);

        assertThat(wi.description).isEqualTo("The real description.");
    }

    @Test
    void priorityChanged_updatesPriority() {
        WorkItem wi = activeWorkItem();
        WebhookEvent event = new WebhookEvent(
                "github", "owner/repo#42", WebhookEventKind.PRIORITY_CHANGED,
                "alice", null, WorkItemPriority.HIGH, null, null, null, null);

        handler.applyTransition(workItemId, wi, event);

        assertThat(wi.priority).isEqualTo(WorkItemPriority.HIGH);
    }

    @Test
    void labelAdded_appendsLabel() {
        WorkItem wi = activeWorkItem();
        WebhookEvent event = new WebhookEvent(
                "github", "owner/repo#42", WebhookEventKind.LABEL_ADDED,
                "alice", null, null, "legal/nda", null, null, null);

        handler.applyTransition(workItemId, wi, event);

        assertThat(wi.labels).anyMatch(l -> "legal/nda".equals(l.path));
    }

    @Test
    void labelAdded_duplicate_notAddedTwice() {
        WorkItem wi = activeWorkItem();
        WorkItemLabel existing = new WorkItemLabel();
        existing.path = "legal/nda";
        wi.labels.add(existing);

        WebhookEvent event = new WebhookEvent(
                "github", "owner/repo#42", WebhookEventKind.LABEL_ADDED,
                "alice", null, null, "legal/nda", null, null, null);

        handler.applyTransition(workItemId, wi, event);

        assertThat(wi.labels).hasSize(1);
    }

    @Test
    void labelRemoved_removesLabel() {
        WorkItem wi = activeWorkItem();
        WorkItemLabel label = new WorkItemLabel();
        label.path = "legal/nda";
        wi.labels.add(label);

        WebhookEvent event = new WebhookEvent(
                "github", "owner/repo#42", WebhookEventKind.LABEL_REMOVED,
                "alice", null, null, "legal/nda", null, null, null);

        handler.applyTransition(workItemId, wi, event);

        assertThat(wi.labels).isEmpty();
    }

    @Test
    void terminalWorkItem_skippedSilently() {
        WorkItem wi = new WorkItem();
        wi.id = workItemId;
        wi.status = WorkItemStatus.COMPLETED;

        WebhookEvent event = new WebhookEvent(
                "github", "owner/repo#42", WebhookEventKind.CLOSED,
                "alice", NormativeResolution.DONE, null, null, null, null, null);

        handler.handle(workItemId, wi, event);

        verifyNoInteractions(workItemService);
    }

    @Test
    void transitionFailure_swallowed() {
        doThrow(new IllegalStateException("wrong status"))
                .when(workItemService).complete(any(), any(), any());

        WebhookEvent event = new WebhookEvent(
                "github", "owner/repo#42", WebhookEventKind.CLOSED,
                "alice", NormativeResolution.DONE, null, null, null, null, null);

        assertThatNoException().isThrownBy(() -> handler.handle(workItemId, activeWorkItem(), event));
    }
}
