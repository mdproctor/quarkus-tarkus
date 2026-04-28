package io.quarkiverse.work.api;

import java.util.ArrayList;
import java.util.List;

public record MultiInstanceConfig(
        int instanceCount,
        int requiredCount,
        ParentRole parentRole,
        String assignmentStrategyName,
        OnThresholdReached onThresholdReached,
        boolean allowSameAssignee,
        List<String> explicitAssignees) {

    public MultiInstanceConfig {
        if (explicitAssignees != null) {
            explicitAssignees = List.copyOf(explicitAssignees);
        }
    }

    public ParentRole effectiveParentRole() {
        return parentRole != null ? parentRole : ParentRole.COORDINATOR;
    }

    public OnThresholdReached effectiveOnThresholdReached() {
        return onThresholdReached != null ? onThresholdReached : OnThresholdReached.CANCEL;
    }

    public String effectiveAssignmentStrategyName() {
        return assignmentStrategyName != null ? assignmentStrategyName : "pool";
    }

    /** Returns a list of validation error messages; empty = valid. */
    public List<String> validate() {
        final List<String> errors = new ArrayList<>();
        if (instanceCount < 1) {
            errors.add("instanceCount must be at least 1");
        }
        if (requiredCount < 1) {
            errors.add("requiredCount must be at least 1");
        }
        if (instanceCount >= 1 && requiredCount >= 1 && requiredCount > instanceCount) {
            errors.add("requiredCount (" + requiredCount + ") cannot exceed instanceCount (" + instanceCount + ")");
        }
        if ("explicit".equals(effectiveAssignmentStrategyName())) {
            if (explicitAssignees == null || explicitAssignees.isEmpty()) {
                errors.add("explicitAssignees required when strategy is 'explicit'");
            } else if (explicitAssignees.size() != instanceCount) {
                errors.add("explicitAssignees size (" + explicitAssignees.size()
                        + ") must match instanceCount (" + instanceCount + ")");
            }
        }
        return errors;
    }
}
