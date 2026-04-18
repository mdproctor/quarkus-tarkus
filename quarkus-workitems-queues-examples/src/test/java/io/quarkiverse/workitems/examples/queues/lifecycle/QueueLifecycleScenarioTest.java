package io.quarkiverse.workitems.examples.queues.lifecycle;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.quarkiverse.workitems.queues.event.QueueEventType;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.common.mapper.TypeRef;

/**
 * End-to-end tests for the queue lifecycle event scenario.
 *
 * <h2>What this tests</h2>
 * <p>
 * Exercises the full ADDED → CHANGED → REMOVED event sequence end-to-end through the
 * real Quarkus stack: REST request → WorkItemService → CDI events → FilterEvaluationObserver
 * → QueueMembershipContext diff → QueueEventLog observation.
 *
 * <h2>Test layers</h2>
 * <ul>
 * <li><strong>Unit:</strong> {@code QueueMembershipContextTest} — diff logic, no Quarkus</li>
 * <li><strong>Integration:</strong> {@code QueueMembershipTrackerTest} — JPA persistence</li>
 * <li><strong>Integration:</strong> {@code WorkItemQueueEventTest} — CDI event delivery</li>
 * <li><strong>End-to-end (this class):</strong> full scenario over HTTP, verifying event ordering</li>
 * </ul>
 */
@QuarkusTest
class QueueLifecycleScenarioTest {

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void scenario_completesSuccessfully() {
        given()
                .post("/queue-examples/lifecycle/run")
                .then()
                .statusCode(200)
                .body("scenarioId", equalTo("queue-lifecycle-demo"))
                .body("steps", hasSize(4));
    }

    @Test
    void step1_createAndLabel_firesAdded() {
        final var response = runScenario();
        final var step = response.steps().get(0);

        assertThat(step.stepNumber()).isEqualTo(1);
        assertThat(step.events()).containsExactly(QueueEventType.ADDED);
    }

    @Test
    void step2_claim_firesChanged() {
        final var response = runScenario();
        final var step = response.steps().get(1);

        assertThat(step.stepNumber()).isEqualTo(2);
        assertThat(step.events()).containsExactly(QueueEventType.CHANGED);
    }

    @Test
    void step3_start_firesChanged() {
        final var response = runScenario();
        final var step = response.steps().get(2);

        assertThat(step.stepNumber()).isEqualTo(3);
        assertThat(step.events()).containsExactly(QueueEventType.CHANGED);
    }

    @Test
    void step4_removeLabelP_firesRemoved() {
        final var response = runScenario();
        final var step = response.steps().get(3);

        assertThat(step.stepNumber()).isEqualTo(4);
        assertThat(step.events()).containsExactly(QueueEventType.REMOVED);
    }

    @Test
    void noSpuriousEvents_noAddedAfterStep1() {
        final var response = runScenario();

        // ADDED should only appear in step 1
        for (int i = 1; i < response.steps().size(); i++) {
            assertThat(response.steps().get(i).events())
                    .as("Step %d should not contain ADDED", i + 1)
                    .doesNotContain(QueueEventType.ADDED);
        }
    }

    @Test
    void noSpuriousEvents_noRemovedBeforeStep4() {
        final var response = runScenario();

        // REMOVED should only appear in step 4
        for (int i = 0; i < response.steps().size() - 1; i++) {
            assertThat(response.steps().get(i).events())
                    .as("Step %d should not contain REMOVED", i + 1)
                    .doesNotContain(QueueEventType.REMOVED);
        }
    }

    @Test
    void noSpuriousEvents_changedNeverFiresWithRemoved() {
        final var response = runScenario();

        for (final var step : response.steps()) {
            if (step.events().contains(QueueEventType.CHANGED)) {
                assertThat(step.events())
                        .as("CHANGED and REMOVED must not fire together in step %d", step.stepNumber())
                        .doesNotContain(QueueEventType.REMOVED);
            }
        }
    }

    @Test
    void eventOrdering_addedThenChangedThenRemoved() {
        final var response = runScenario();
        final var allEventTypes = response.steps().stream()
                .flatMap(s -> s.events().stream())
                .toList();

        // Full expected sequence across all steps
        assertThat(allEventTypes).containsExactly(
                QueueEventType.ADDED, // step 1: label added
                QueueEventType.CHANGED, // step 2: claimed
                QueueEventType.CHANGED, // step 3: started
                QueueEventType.REMOVED); // step 4: label removed
    }

    // ── Idempotency — running twice produces consistent results ───────────────

    @Test
    void runTwice_eachRunProducesCorrectSequence() {
        final var first = runScenario();
        final var second = runScenario();

        final var firstSequence = flatten(first);
        final var secondSequence = flatten(second);

        assertThat(firstSequence).containsExactlyElementsOf(secondSequence);
        assertThat(firstSequence).containsExactly(
                QueueEventType.ADDED, QueueEventType.CHANGED,
                QueueEventType.CHANGED, QueueEventType.REMOVED);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private QueueLifecycleResponse runScenario() {
        return given()
                .post("/queue-examples/lifecycle/run")
                .then()
                .statusCode(200)
                .extract()
                .as(new TypeRef<>() {
                });
    }

    private List<QueueEventType> flatten(final QueueLifecycleResponse response) {
        return response.steps().stream()
                .flatMap(s -> s.events().stream())
                .toList();
    }
}
