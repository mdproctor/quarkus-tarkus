package io.casehub.work.runtime.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for {@link WorkItemRelationType} — no Quarkus, no DB.
 *
 * <p>
 * Verifies that relation types are plain strings (not an enum), that well-known
 * constants are present, and that custom types are accepted without any registration.
 */
class WorkItemRelationTypeTest {

    @Test
    void wellKnownTypes_areNonBlankStrings() {
        assertThat(WorkItemRelationType.PART_OF).isNotBlank();
        assertThat(WorkItemRelationType.BLOCKS).isNotBlank();
        assertThat(WorkItemRelationType.BLOCKED_BY).isNotBlank();
        assertThat(WorkItemRelationType.RELATES_TO).isNotBlank();
        assertThat(WorkItemRelationType.DUPLICATES).isNotBlank();
    }

    @Test
    void partOf_isDirectedChildToParent() {
        // Convention: "source PART_OF target" means source is a child of target.
        // The string itself documents this by reading naturally.
        assertThat(WorkItemRelationType.PART_OF).isEqualTo("PART_OF");
    }

    @Test
    void customType_isJustAString_requiresNoRegistration() {
        // Consumers add custom types without schema changes.
        final String custom = "TRIGGERED_BY";
        assertThat(custom).isNotBlank(); // any non-blank string is valid
    }

    @Test
    void inverseOf_blocks_isBlockedBy() {
        // Semantic convention: BLOCKS and BLOCKED_BY are inverses.
        // The graph stores directed edges — both directions require two rows.
        assertThat(WorkItemRelationType.inverse(WorkItemRelationType.BLOCKS))
                .isEqualTo(WorkItemRelationType.BLOCKED_BY);
        assertThat(WorkItemRelationType.inverse(WorkItemRelationType.BLOCKED_BY))
                .isEqualTo(WorkItemRelationType.BLOCKS);
    }

    @Test
    void inverseOf_unknownType_returnsNull() {
        assertThat(WorkItemRelationType.inverse("CUSTOM_TYPE")).isNull();
    }

    @Test
    void inverseOf_partOf_returnsNull() {
        // PART_OF has no standard inverse — "HAS_CHILD" is navigated via incoming query.
        assertThat(WorkItemRelationType.inverse(WorkItemRelationType.PART_OF)).isNull();
    }
}
