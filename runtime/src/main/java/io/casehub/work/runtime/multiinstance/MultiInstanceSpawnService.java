package io.casehub.work.runtime.multiinstance;

import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.transaction.Transactional;

import io.casehub.work.api.InstanceAssignmentStrategy;
import io.casehub.work.api.MultiInstanceConfig;
import io.casehub.work.api.MultiInstanceContext;
import io.casehub.work.api.OnThresholdReached;
import io.casehub.work.api.ParentRole;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemCreateRequest;
import io.casehub.work.runtime.model.WorkItemRelation;
import io.casehub.work.runtime.model.WorkItemRelationType;
import io.casehub.work.runtime.model.WorkItemSpawnGroup;
import io.casehub.work.runtime.model.WorkItemTemplate;
import io.casehub.work.runtime.service.WorkItemService;

/**
 * Creates a multi-instance group: a parent WorkItem + N child instances + a
 * {@link WorkItemSpawnGroup} that owns the M-of-N completion policy.
 *
 * <p>
 * All three artefacts are created inside a single transaction (the caller's or
 * one started here via {@code @Transactional}). The assignment strategy named
 * on the template is resolved from CDI by {@link Named} qualifier; when absent
 * or {@code "pool"}, the default {@link PoolAssignmentStrategy} is used.
 */
@ApplicationScoped
public class MultiInstanceSpawnService {

    @Inject
    WorkItemService workItemService;

    @Inject
    @Named("pool")
    InstanceAssignmentStrategy defaultStrategy;

    @Inject
    @Any
    Instance<InstanceAssignmentStrategy> strategies;

    /**
     * Create a multi-instance group: parent WorkItem + N child instances + spawn group.
     * All created in the caller's transaction.
     *
     * @param template the template driving the group; must have {@code instanceCount} set
     * @param titleOverride optional parent title; defaults to {@code template.name}
     * @param createdBy the actor (user or system) triggering instantiation
     * @return the parent WorkItem
     */
    @Transactional
    public WorkItem createGroup(final WorkItemTemplate template, final String titleOverride,
            final String createdBy) {
        final boolean isCoordinator = template.parentRole == null
                || ParentRole.COORDINATOR.name().equals(template.parentRole);

        // 1. Create parent
        final WorkItemCreateRequest parentReq = buildParentRequest(template, titleOverride, createdBy, isCoordinator);
        final WorkItem parent = workItemService.create(parentReq);

        // 2. Create WorkItemSpawnGroup with M-of-N policy
        final WorkItemSpawnGroup group = new WorkItemSpawnGroup();
        group.parentId = parent.id;
        group.idempotencyKey = "multi-instance:" + parent.id;
        group.instanceCount = template.instanceCount;
        group.requiredCount = template.requiredCount;
        group.onThresholdReached = template.onThresholdReached != null
                ? template.onThresholdReached
                : OnThresholdReached.CANCEL.name();
        group.allowSameAssignee = Boolean.TRUE.equals(template.allowSameAssignee);
        group.parentRole = template.parentRole != null ? template.parentRole : ParentRole.COORDINATOR.name();
        group.persist();

        // 3. Create N child instances and wire PART_OF relations
        final List<WorkItem> children = new ArrayList<>();
        for (int i = 0; i < template.instanceCount; i++) {
            final WorkItemCreateRequest childReq = buildChildRequest(template, createdBy, i, group);
            final WorkItem child = workItemService.create(childReq);
            child.parentId = parent.id;

            final WorkItemRelation rel = new WorkItemRelation();
            rel.sourceId = child.id;
            rel.targetId = parent.id;
            rel.relationType = WorkItemRelationType.PART_OF;
            rel.createdBy = "system:multi-instance:" + group.id;
            rel.persist();

            children.add(child);
        }

        // 4. Apply assignment strategy (may mutate candidateGroups/candidateUsers/assigneeId on children)
        final InstanceAssignmentStrategy strategy = resolveStrategy(template.assignmentStrategy);
        final MultiInstanceConfig config = new MultiInstanceConfig(
                template.instanceCount,
                template.requiredCount != null ? template.requiredCount : template.instanceCount,
                null,
                template.assignmentStrategy,
                null,
                Boolean.TRUE.equals(template.allowSameAssignee),
                null);
        strategy.assign((List) children, new MultiInstanceContext(parent, config));

        return parent;
    }

    private WorkItemCreateRequest buildParentRequest(final WorkItemTemplate template,
            final String titleOverride, final String createdBy, final boolean isCoordinator) {
        final String title = (titleOverride != null && !titleOverride.isBlank())
                ? titleOverride
                : template.name;
        return new WorkItemCreateRequest(
                title,
                template.description,
                template.category,
                null, // formKey — not templated
                template.priority,
                null, // assigneeId — coordinator has none; participant uses candidateGroups routing
                isCoordinator ? null : template.candidateGroups,
                isCoordinator ? null : template.candidateUsers,
                template.requiredCapabilities,
                createdBy,
                template.defaultPayload,
                null, // claimDeadline
                null, // expiresAt
                null, // followUpDate
                null, // labels — applied separately if needed
                null, // confidenceScore
                null, // callerRef
                null, // defaultClaimBusinessHours — coordinator has no deadline
                isCoordinator ? null : template.defaultExpiryBusinessHours);
    }

    private WorkItemCreateRequest buildChildRequest(final WorkItemTemplate template,
            final String createdBy, final int index, final WorkItemSpawnGroup group) {
        return new WorkItemCreateRequest(
                template.name + " [" + (index + 1) + "/" + template.instanceCount + "]",
                template.description,
                template.category,
                null, // formKey
                template.priority,
                null, // assigneeId — strategy handles assignment
                template.candidateGroups,
                template.candidateUsers,
                template.requiredCapabilities,
                "system:multi-instance:" + group.id,
                template.defaultPayload,
                null, // claimDeadline
                null, // expiresAt
                null, // followUpDate
                null, // labels
                null, // confidenceScore
                null, // callerRef
                template.defaultClaimBusinessHours,
                template.defaultExpiryBusinessHours);
    }

    /**
     * Resolve the assignment strategy by name from CDI.
     * Falls back to the default {@link PoolAssignmentStrategy} when the name is null, blank, or {@code "pool"}.
     *
     * @param name the CDI {@link Named} qualifier value; may be null
     * @return the resolved strategy; never null
     */
    private InstanceAssignmentStrategy resolveStrategy(final String name) {
        if (name == null || name.isBlank() || "pool".equals(name)) {
            return defaultStrategy;
        }
        for (final InstanceAssignmentStrategy s : strategies) {
            final Named named = s.getClass().getAnnotation(Named.class);
            if (named != null && name.equals(named.value())) {
                return s;
            }
        }
        return defaultStrategy;
    }
}
