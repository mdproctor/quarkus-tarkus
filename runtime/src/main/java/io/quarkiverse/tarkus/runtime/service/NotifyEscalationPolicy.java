package io.quarkiverse.tarkus.runtime.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.quarkiverse.tarkus.runtime.model.WorkItem;

@ApplicationScoped
public class NotifyEscalationPolicy implements EscalationPolicy {

    private static final Logger LOG = Logger.getLogger(NotifyEscalationPolicy.class);

    @Inject
    Event<WorkItemExpiredEvent> expiredEvent;

    @Override
    public void onExpired(WorkItem workItem) {
        LOG.warnf("WorkItem %s expired (was %s)", workItem.id, workItem.status);
        expiredEvent.fire(new WorkItemExpiredEvent(workItem.id, workItem.status));
    }

    @Override
    public void onUnclaimedPastDeadline(WorkItem workItem) {
        LOG.warnf("WorkItem %s unclaimed past deadline", workItem.id);
    }
}
