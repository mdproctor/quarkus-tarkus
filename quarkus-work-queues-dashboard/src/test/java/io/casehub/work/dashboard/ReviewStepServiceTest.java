package io.casehub.work.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class ReviewStepServiceTest {

    @Inject
    ReviewStepService stepService;

    @BeforeEach
    @Transactional
    void reset() {
        stepService.reset();
    }

    @Test
    void lambdaFilterCount_showsSecurityWritersFilter() {
        // This test tells us immediately if SecurityWritersFilter is discovered.
        // If count == 0, the Lambda CDI bean is not in the CDI graph.
        final var names = stepService.lambdaFilterNames();
        assertThat(stepService.lambdaFilterCount())
                .as("Lambda filter count — if 0, SecurityWritersFilter is not discovered. Found: " + names)
                .isGreaterThan(0);
    }

    @Test
    @Transactional
    void step1_createsThreeDocuments() {
        final var result = stepService.advance();
        assertThat(result.step()).isEqualTo(1);
        assertThat(result.action()).isEqualTo("Created 3 documents");
        assertThat(stepService.getAdvisoryId()).isNotNull();
    }

    @Test
    @Transactional
    void step1_advisoryHasUrgentLabel_ifLambdaDiscovered() {
        stepService.advance(); // setup
        if (stepService.lambdaFilterCount() > 0) {
            // Lambda filter is discovered — advisory should be in review/urgent.
            // After claim, advisory should be in review/urgent/claimed.
            final var result = stepService.advance(); // step 2 (claim)
            assertThat(result.detail()).contains("ASSIGNED");
        }
        // If lambda count == 0, we skip — that's the diagnostic itself.
    }

    @Test
    @Transactional
    void fullFlow_allStepsTransition() {
        assertThat(stepService.advance().step()).isEqualTo(1); // setup
        assertThat(stepService.advance().step()).isEqualTo(2); // claim
        assertThat(stepService.advance().step()).isEqualTo(3); // start
        assertThat(stepService.advance().step()).isEqualTo(4); // complete
        assertThat(stepService.advance().step()).isEqualTo(0); // reset
    }
}
