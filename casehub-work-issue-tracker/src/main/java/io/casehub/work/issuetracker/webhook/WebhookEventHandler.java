package io.casehub.work.issuetracker.webhook;

import java.util.ArrayList;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

import io.casehub.work.issuetracker.repository.IssueLinkStore;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemLabel;
import io.casehub.work.runtime.model.WorkItemStatus;
import io.casehub.work.runtime.repository.WorkItemStore;
import io.casehub.work.runtime.service.WorkItemService;

/**
 * Applies normalised {@link WebhookEvent} records to WorkItem state.
 * Called by tracker-specific webhook resources after HMAC verification and parsing.
 * Tracker vocabulary never reaches this class — all events arrive already normalised.
 */
@ApplicationScoped
public class WebhookEventHandler {

    private static final Logger LOG = Logger.getLogger(WebhookEventHandler.class);
    private static final String LINKED_WORKITEM_FOOTER = "\n\n---\n*Linked WorkItem:";

    @Inject
    IssueLinkStore linkStore;

    @Inject
    WorkItemStore workItemStore;

    @Inject
    WorkItemService workItemService;

    /** Package-private constructor for unit testing without CDI. */
    WebhookEventHandler(
            final IssueLinkStore linkStore,
            final WorkItemStore workItemStore,
            final WorkItemService workItemService) {
        this.linkStore = linkStore;
        this.workItemStore = workItemStore;
        this.workItemService = workItemService;
    }

    WebhookEventHandler() {
        // CDI no-arg constructor
    }

    /**
     * Look up all WorkItems linked to the event's externalRef and apply the transition to each.
     * Failures per WorkItem are logged and swallowed — prevents tracker retries.
     */
    @Transactional
    public void handle(final WebhookEvent event) {
        final var links = linkStore.findByTrackerRef(event.trackerType(), event.externalRef());

        if (links.isEmpty()) {
            LOG.debugf("No WorkItem linked to %s:%s — ignoring", event.trackerType(), event.externalRef());
            return;
        }

        for (final var link : links) {
            workItemStore.get(link.workItemId)
                    .ifPresent(wi -> handle(link.workItemId, wi, event));
        }
    }

    /** Package-private for unit testing. */
    void handle(final UUID workItemId, final WorkItem workItem, final WebhookEvent event) {
        if (workItem.status != null && workItem.status.isTerminal()) {
            LOG.debugf("WorkItem %s is terminal (%s) — skipping %s event",
                    workItemId, workItem.status, event.eventKind());
            return;
        }
        try {
            applyTransition(workItemId, workItem, event);
        } catch (final Exception e) {
            LOG.warnf("Failed to apply %s event to WorkItem %s: %s",
                    event.eventKind(), workItemId, e.getMessage());
        }
    }

    /** Package-private for unit testing. */
    void applyTransition(final UUID workItemId, final WorkItem workItem, final WebhookEvent event) {
        switch (event.eventKind()) {
            case CLOSED -> applyClosed(workItemId, event);
            case ASSIGNED -> applyAssigned(workItemId, workItem, event);
            case UNASSIGNED -> workItemService.release(workItemId, event.actor());
            case TITLE_CHANGED -> workItem.title = event.newTitle();
            case DESCRIPTION_CHANGED -> workItem.description = stripFooter(event.newDescription());
            case PRIORITY_CHANGED -> workItem.priority = event.newPriority();
            case LABEL_ADDED -> addLabel(workItem, event.labelValue());
            case LABEL_REMOVED -> removeLabel(workItem, event.labelValue());
        }
    }

    private void applyClosed(final UUID workItemId, final WebhookEvent event) {
        switch (event.normativeResolution()) {
            case DONE -> workItemService.complete(workItemId, event.actor(), null);
            case DECLINE -> workItemService.cancel(workItemId, event.actor(), null);
            case FAILURE -> workItemService.reject(workItemId, event.actor(), null);
        }
    }

    private void applyAssigned(final UUID workItemId, final WorkItem workItem, final WebhookEvent event) {
        if (workItem.status == WorkItemStatus.PENDING) {
            workItemService.claim(workItemId, event.newAssignee());
        } else {
            workItem.assigneeId = event.newAssignee();
        }
    }

    private String stripFooter(final String description) {
        if (description == null) return null;
        final int idx = description.indexOf(LINKED_WORKITEM_FOOTER);
        return idx >= 0 ? description.substring(0, idx) : description;
    }

    private void addLabel(final WorkItem workItem, final String path) {
        if (workItem.labels == null) workItem.labels = new ArrayList<>();
        final boolean exists = workItem.labels.stream().anyMatch(l -> path.equals(l.path));
        if (!exists) {
            final WorkItemLabel label = new WorkItemLabel();
            label.path = path;
            workItem.labels.add(label);
        }
    }

    private void removeLabel(final WorkItem workItem, final String path) {
        if (workItem.labels != null) {
            workItem.labels.removeIf(l -> path.equals(l.path));
        }
    }
}
