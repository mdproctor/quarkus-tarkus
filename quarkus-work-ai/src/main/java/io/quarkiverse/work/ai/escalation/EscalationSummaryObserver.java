package io.quarkiverse.work.ai.escalation;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.quarkiverse.work.api.WorkEventType;
import io.quarkiverse.work.api.WorkLifecycleEvent;

/**
 * CDI observer that generates an LLM escalation summary when a WorkItem
 * fires an {@code EXPIRED} or {@code CLAIM_EXPIRED} lifecycle event.
 *
 * <p>
 * The observer runs {@link TransactionPhase#AFTER_SUCCESS} so the WorkItem's
 * final state is committed before the summary is generated and persisted in
 * its own transaction.
 */
@ApplicationScoped
public class EscalationSummaryObserver {

    private static final Logger LOG = Logger.getLogger(EscalationSummaryObserver.class);

    @Inject
    EscalationSummaryService summaryService;

    /**
     * React to escalation events and generate a summary.
     *
     * @param event the lifecycle event; EXPIRED and CLAIM_EXPIRED are handled
     */
    void onEscalation(@Observes final WorkLifecycleEvent event) {
        final WorkEventType type = event.eventType();
        if (type != WorkEventType.EXPIRED && type != WorkEventType.CLAIM_EXPIRED) {
            return;
        }
        try {
            final Object source = event.source();
            if (source instanceof io.quarkiverse.work.runtime.model.WorkItem wi) {
                summaryService.buildSummary(wi.id, type.name()).persist();
            }
        } catch (final Exception e) {
            LOG.warnf("Failed to generate escalation summary for event %s: %s",
                    type, e.getMessage());
        }
    }
}
