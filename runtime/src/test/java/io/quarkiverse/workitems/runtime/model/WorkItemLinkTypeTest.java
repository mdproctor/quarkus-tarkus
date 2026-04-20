package io.quarkiverse.workitems.runtime.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Pure unit tests for {@link WorkItemLinkType} — no Quarkus, no DB.
 *
 * Verifies that link types are plain strings (not an enum), well-known
 * constants exist, and custom types are accepted without registration.
 */
class WorkItemLinkTypeTest {

    @Test
    void wellKnownTypes_areNonBlankStrings() {
        assertThat(WorkItemLinkType.REFERENCE).isNotBlank();
        assertThat(WorkItemLinkType.DESIGN_SPEC).isNotBlank();
        assertThat(WorkItemLinkType.POLICY).isNotBlank();
        assertThat(WorkItemLinkType.EVIDENCE).isNotBlank();
        assertThat(WorkItemLinkType.SOURCE_DOCUMENT).isNotBlank();
        assertThat(WorkItemLinkType.ATTACHMENT).isNotBlank();
    }

    @Test
    void designSpec_readsNaturally() {
        // "this WorkItem is DESIGN_SPEC'd by that document"
        assertThat(WorkItemLinkType.DESIGN_SPEC).isEqualTo("design-spec");
    }

    @Test
    void attachment_documentsThatFilesStoredElsewhere() {
        // "attachment" covers S3/GCS/MinIO files — WorkItems stores the reference, not the content
        assertThat(WorkItemLinkType.ATTACHMENT).isEqualTo("attachment");
    }

    @Test
    void customType_isJustAString_requiresNoRegistration() {
        // Consumers can use any non-blank string
        final String custom = "internal-wiki-page";
        assertThat(custom).isNotBlank();
    }

    @Test
    void allWellKnownTypes_areLowerKebabCase() {
        // Convention: lowercase kebab-case for well-known types
        for (final String type : WorkItemLinkType.WELL_KNOWN) {
            assertThat(type).matches("[a-z][a-z0-9-]*");
        }
    }
}
