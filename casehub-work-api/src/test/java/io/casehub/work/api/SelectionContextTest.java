package io.casehub.work.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SelectionContextTest {

    @Test
    void constructor_setsAllFields() {
        final SelectionContext ctx = new SelectionContext(
                "finance", "HIGH", "audit,legal", "finance-team", "alice,bob", null, null);
        assertThat(ctx.category()).isEqualTo("finance");
        assertThat(ctx.priority()).isEqualTo("HIGH");
        assertThat(ctx.requiredCapabilities()).isEqualTo("audit,legal");
        assertThat(ctx.candidateGroups()).isEqualTo("finance-team");
        assertThat(ctx.candidateUsers()).isEqualTo("alice,bob");
    }

    @Test
    void constructor_acceptsNullFields() {
        final SelectionContext ctx = new SelectionContext(null, null, null, null, null, null, null);
        assertThat(ctx.category()).isNull();
        assertThat(ctx.candidateGroups()).isNull();
    }

    @Test
    void record_storesNewFields() {
        final var ctx = new SelectionContext(null, null, null, null, null,
                "Review NDA", "Please review this NDA.");
        assertThat(ctx.title()).isEqualTo("Review NDA");
        assertThat(ctx.description()).isEqualTo("Please review this NDA.");
    }

    @Test
    void record_newFieldsNullable() {
        final var ctx = new SelectionContext(null, null, null, null, null, null, null);
        assertThat(ctx.title()).isNull();
        assertThat(ctx.description()).isNull();
    }
}
