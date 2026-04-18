package io.quarkiverse.workitems.runtime.event;

import java.time.Instant;
import java.util.UUID;

import io.quarkiverse.workitems.runtime.model.WorkItemStatus;

/**
 * CDI event fired on every WorkItem lifecycle transition.
 * Consuming applications observe this with {@code @Observes WorkItemLifecycleEvent}
 * and can bridge it to any messaging system.
 *
 * <p>
 * The optional {@code rationale} and {@code planRef} fields are populated when
 * available (e.g. rejection reason, policy reference) and are {@code null} otherwise.
 * Observers that don't use them can safely ignore them.
 *
 * <h2>Firing contract — fires AFTER the mutation is persisted</h2>
 * <p>
 * This event is fired by {@link io.quarkiverse.workitems.runtime.service.WorkItemService}
 * <em>after</em> the WorkItem has been mutated and written to the store via
 * {@code workItemStore.put(workItem)}. By the time any observer receives this event,
 * the WorkItem's new state is already the current state in the store.
 *
 * <p>
 * <strong>This has a critical consequence for observers that need the pre-mutation state.</strong>
 * If an observer calls {@code workItemStore.get(event.workItemId())} inside its handler,
 * it receives the <em>post</em>-mutation entity — the "before" is gone. Observers that
 * must compare before and after (for example, to detect which queues a WorkItem entered
 * or left) must maintain their own record of the previous state between events.
 *
 * <p>
 * The {@code status} field in this event records the status <em>after</em> the transition.
 * No "previous status" field is provided in the event itself.
 *
 * <h2>Sequence</h2>
 *
 * <pre>
 * WorkItemService.claim(id, actor)
 *   1. workItemStore.get(id)           ← loads WorkItem (status=PENDING, labels=[...])
 *   2. wi.status = ASSIGNED            ← mutates in memory
 *   3. workItemStore.put(wi)           ← persists; store now has status=ASSIGNED
 *   4. lifecycleEvent.fire(ASSIGNED)   ← fires AFTER step 3
 *
 * Observer receives event:
 *   5. workItemStore.get(id)           ← returns status=ASSIGNED  ← POST-MUTATION
 *      (no way to recover the pre-mutation state from the store)
 * </pre>
 */
public record WorkItemLifecycleEvent(
        String type,
        String source,
        String subject,
        UUID workItemId,
        WorkItemStatus status,
        Instant occurredAt,
        String actor,
        String detail,
        String rationale,
        String planRef) {

    /**
     * Creates a lifecycle event with the standard WorkItems type prefix.
     *
     * @param eventName the audit event name (e.g. "CREATED") — lowercased automatically
     * @param workItemId the affected WorkItem
     * @param status the status AFTER the transition
     * @param actor who triggered the transition
     * @param detail optional JSON detail (nullable)
     */
    public static WorkItemLifecycleEvent of(final String eventName, final UUID workItemId,
            final WorkItemStatus status, final String actor, final String detail) {
        return new WorkItemLifecycleEvent(
                "io.quarkiverse.workitems.workitem." + eventName.toLowerCase(),
                "/workitems/" + workItemId,
                workItemId.toString(),
                workItemId,
                status,
                Instant.now(),
                actor,
                detail,
                null,
                null);
    }

    /**
     * Creates a lifecycle event with rationale and plan reference.
     * Used when the actor's stated basis and governing policy are known.
     *
     * @param eventName the audit event name
     * @param workItemId the affected WorkItem
     * @param status the status AFTER the transition
     * @param actor who triggered the transition
     * @param detail optional JSON detail (nullable)
     * @param rationale the actor's stated basis for the decision (nullable)
     * @param planRef the policy/procedure version that governed this action (nullable)
     */
    public static WorkItemLifecycleEvent of(final String eventName, final UUID workItemId,
            final WorkItemStatus status, final String actor, final String detail,
            final String rationale, final String planRef) {
        return new WorkItemLifecycleEvent(
                "io.quarkiverse.workitems.workitem." + eventName.toLowerCase(),
                "/workitems/" + workItemId,
                workItemId.toString(),
                workItemId,
                status,
                Instant.now(),
                actor,
                detail,
                rationale,
                planRef);
    }
}
