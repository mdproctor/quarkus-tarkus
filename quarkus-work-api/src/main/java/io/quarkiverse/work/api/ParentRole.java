package io.quarkiverse.work.api;

/**
 * Role of the parent WorkItem in a multi-instance group.
 */
public enum ParentRole {
    /**
     * Parent is a coordinator placeholder — does not appear in any inbox, purely structural.
     */
    COORDINATOR,
    /**
     * Parent is a participant — real work item with its own inbox presence and lifecycle.
     */
    PARTICIPANT
}
