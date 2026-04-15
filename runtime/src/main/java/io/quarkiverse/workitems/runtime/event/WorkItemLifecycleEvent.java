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
     * Creates a lifecycle event with the standard Tarkus type prefix.
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
                "/tarkus/workitems/" + workItemId,
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
                "/tarkus/workitems/" + workItemId,
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
