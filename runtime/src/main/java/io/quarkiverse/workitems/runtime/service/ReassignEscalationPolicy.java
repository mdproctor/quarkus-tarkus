package io.quarkiverse.workitems.runtime.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.workitems.runtime.model.WorkItem;
import io.quarkiverse.workitems.runtime.model.WorkItemStatus;
import io.quarkiverse.workitems.runtime.repository.WorkItemRepository;

@ApplicationScoped
public class ReassignEscalationPolicy implements EscalationPolicy {

    @Inject
    WorkItemRepository workItemRepo;

    @Inject
    NotifyEscalationPolicy notifyPolicy;

    @Override
    public void onExpired(WorkItem workItem) {
        if (hasCandidates(workItem)) {
            workItem.assigneeId = null;
            workItem.status = WorkItemStatus.PENDING;
            workItemRepo.save(workItem);
        } else {
            notifyPolicy.onExpired(workItem);
        }
    }

    @Override
    public void onUnclaimedPastDeadline(WorkItem workItem) {
        if (hasCandidates(workItem)) {
            workItem.assigneeId = null;
            // status stays PENDING — already unclaimed
            workItemRepo.save(workItem);
        } else {
            notifyPolicy.onUnclaimedPastDeadline(workItem);
        }
    }

    private boolean hasCandidates(WorkItem workItem) {
        return (workItem.candidateGroups != null && !workItem.candidateGroups.isBlank())
                || (workItem.candidateUsers != null && !workItem.candidateUsers.isBlank());
    }
}
