package io.quarkiverse.work.api;

/**
 * SPI for escalation behaviour when work stalls past a deadline.
 *
 * <p>
 * Implementations in quarkus-work cast {@code event.source()} to {@code WorkItem}
 * and can inspect {@code event.eventType()} to distinguish between
 * {@link WorkEventType#EXPIRED} (completion deadline missed) and
 * {@link WorkEventType#CLAIM_EXPIRED} (claim deadline missed without assignment).
 *
 * <p>
 * Implementations are qualified with {@code @ExpiryEscalation} or
 * {@code @ClaimEscalation} in quarkus-work to distinguish the two injection points.
 */
public interface EscalationPolicy {

    /**
     * React to a stalled work unit.
     *
     * @param event the lifecycle event that triggered escalation; never null.
     *        Use {@code event.eventType()} to determine the escalation reason,
     *        {@code event.source()} to access the concrete work unit.
     */
    void escalate(WorkLifecycleEvent event);
}
