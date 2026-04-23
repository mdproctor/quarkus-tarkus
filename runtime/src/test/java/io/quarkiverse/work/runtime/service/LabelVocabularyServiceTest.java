package io.quarkiverse.work.runtime.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.quarkiverse.work.runtime.model.VocabularyScope;

class LabelVocabularyServiceTest {

    @Test
    void vocabularyScope_hasCorrectHierarchy() {
        assertThat(VocabularyScope.GLOBAL.ordinal()).isLessThan(VocabularyScope.ORG.ordinal());
        assertThat(VocabularyScope.ORG.ordinal()).isLessThan(VocabularyScope.TEAM.ordinal());
        assertThat(VocabularyScope.TEAM.ordinal()).isLessThan(VocabularyScope.PERSONAL.ordinal());
    }

    @Test
    void vocabularyScope_fourValues() {
        assertThat(VocabularyScope.values()).hasSize(4);
    }

    @Test
    void isScopeAccessible_globalVisibleToAll() {
        assertThat(LabelVocabularyService.isScopeAccessible(VocabularyScope.GLOBAL, VocabularyScope.PERSONAL)).isTrue();
        assertThat(LabelVocabularyService.isScopeAccessible(VocabularyScope.GLOBAL, VocabularyScope.TEAM)).isTrue();
        assertThat(LabelVocabularyService.isScopeAccessible(VocabularyScope.GLOBAL, VocabularyScope.ORG)).isTrue();
        assertThat(LabelVocabularyService.isScopeAccessible(VocabularyScope.GLOBAL, VocabularyScope.GLOBAL)).isTrue();
    }

    @Test
    void isScopeAccessible_personalNotVisibleToTeam() {
        assertThat(LabelVocabularyService.isScopeAccessible(VocabularyScope.PERSONAL, VocabularyScope.TEAM)).isFalse();
        assertThat(LabelVocabularyService.isScopeAccessible(VocabularyScope.PERSONAL, VocabularyScope.ORG)).isFalse();
        assertThat(LabelVocabularyService.isScopeAccessible(VocabularyScope.PERSONAL, VocabularyScope.GLOBAL)).isFalse();
    }

    @Test
    void matchesPattern_exactMatch() {
        assertThat(LabelVocabularyService.matchesPattern("legal", "legal")).isTrue();
        assertThat(LabelVocabularyService.matchesPattern("legal", "legal/contracts")).isFalse();
    }

    @Test
    void matchesPattern_singleWildcard() {
        assertThat(LabelVocabularyService.matchesPattern("legal/*", "legal/contracts")).isTrue();
        assertThat(LabelVocabularyService.matchesPattern("legal/*", "legal/contracts/nda")).isFalse();
        assertThat(LabelVocabularyService.matchesPattern("legal/*", "legal")).isFalse();
    }

    @Test
    void matchesPattern_multiWildcard() {
        assertThat(LabelVocabularyService.matchesPattern("legal/**", "legal/contracts")).isTrue();
        assertThat(LabelVocabularyService.matchesPattern("legal/**", "legal/contracts/nda")).isTrue();
        assertThat(LabelVocabularyService.matchesPattern("legal/**", "legal")).isFalse();
    }
}
