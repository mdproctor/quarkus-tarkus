package io.quarkiverse.work.runtime.model;

/**
 * Scope of a {@link LabelVocabulary}, forming a visibility hierarchy from broadest to narrowest.
 *
 * <p>
 * A vocabulary at a given scope is visible to all scopes below it:
 * GLOBAL terms are visible to everyone; PERSONAL terms are visible only to the declaring user.
 * The ordinal ordering reflects this hierarchy — lower ordinal = broader scope.
 */
public enum VocabularyScope {
    /** Platform-wide; accessible everywhere. No owner required. */
    GLOBAL,

    /** Organisation-level; accessible within the owning org and its teams. */
    ORG,

    /** Team-level; accessible within the owning team only. */
    TEAM,

    /** Personal; accessible only to the declaring user. */
    PERSONAL
}
