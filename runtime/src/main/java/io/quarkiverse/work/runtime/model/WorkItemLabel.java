package io.quarkiverse.work.runtime.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/**
 * A label applied to a {@link WorkItem}, identified by a {@code /}-separated path.
 *
 * <p>
 * Labels are either {@link LabelPersistence#MANUAL} (human-applied, persists until
 * explicitly removed) or {@link LabelPersistence#INFERRED} (filter-applied, recomputed
 * on every WorkItem mutation).
 *
 * <p>
 * A label path is a {@code /}-separated sequence of terms, e.g. {@code legal},
 * {@code legal/contracts}, {@code legal/contracts/nda}. A single-term label is
 * structurally identical to a multi-segment path.
 */
@Embeddable
public class WorkItemLabel {

    /** The label path, e.g. {@code legal/contracts/nda}. */
    @Column(name = "path", nullable = false, length = 500)
    public String path;

    /**
     * How this label was applied and how it is maintained.
     *
     * @see LabelPersistence
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "persistence", nullable = false, length = 20)
    public LabelPersistence persistence;

    /**
     * Who or what applied this label.
     * For {@link LabelPersistence#MANUAL}: the userId who applied it.
     * For {@link LabelPersistence#INFERRED}: the filterId that applied it (may be null).
     */
    @Column(name = "applied_by", length = 255)
    public String appliedBy;

    /** JPA requires a no-arg constructor. */
    public WorkItemLabel() {
    }

    /**
     * Convenience constructor.
     *
     * @param path the label path; must not be null or blank
     * @param persistence how the label is maintained
     * @param appliedBy userId (MANUAL) or filterId (INFERRED); may be null
     */
    public WorkItemLabel(final String path, final LabelPersistence persistence, final String appliedBy) {
        this.path = path;
        this.persistence = persistence;
        this.appliedBy = appliedBy;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WorkItemLabel other)) {
            return false;
        }
        return java.util.Objects.equals(path, other.path)
                && persistence == other.persistence
                && java.util.Objects.equals(appliedBy, other.appliedBy);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(path, persistence, appliedBy);
    }
}
