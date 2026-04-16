package io.quarkiverse.workitems.examples.queues.review;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.workitems.queues.model.FilterAction;
import io.quarkiverse.workitems.queues.model.FilterScope;
import io.quarkiverse.workitems.queues.service.WorkItemFilterBean;
import io.quarkiverse.workitems.runtime.model.WorkItem;

/**
 * Lambda filter: any document submitted by the security writing team is urgent,
 * regardless of the priority field on the WorkItem.
 *
 * <p>
 * This demonstrates the Lambda CDI filter pattern — business rules that require
 * Java logic rather than an expression string. A document flagged as security-related
 * ({@code candidateGroups} contains {@code "security-writers"}) must always enter the
 * urgent review queue to meet compliance publication timelines, even if submitted with
 * {@code NORMAL} priority by mistake.
 *
 * <p>
 * Lambda filters are always active while deployed. They are discovered automatically
 * by {@code LambdaFilterRegistry} via CDI {@code Instance<WorkItemFilterBean>}.
 */
@ApplicationScoped
public class SecurityWritersFilter implements WorkItemFilterBean {

    @Override
    public boolean matches(final WorkItem workItem) {
        // Only route active (non-terminal) items — completed/cancelled/rejected items
        // should leave all queues, not be re-assigned to a tier.
        return workItem.candidateGroups != null
                && workItem.candidateGroups.contains("security-writers")
                && workItem.status != null
                && !workItem.status.isTerminal();
    }

    @Override
    public List<FilterAction> actions() {
        return List.of(FilterAction.applyLabel("review/urgent"));
    }

    @Override
    public FilterScope scope() {
        return FilterScope.ORG;
    }
}
