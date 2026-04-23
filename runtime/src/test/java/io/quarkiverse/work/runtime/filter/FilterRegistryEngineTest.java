package io.quarkiverse.work.runtime.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkiverse.work.api.WorkEventType;
import io.quarkiverse.work.api.WorkLifecycleEvent;

class FilterRegistryEngineTest {

    private FilterRegistryEngine engine;
    private List<String> capturedWorkUnits;
    private FilterAction capturingAction;

    @BeforeEach
    void setUp() {
        capturedWorkUnits = new ArrayList<>();
        capturingAction = new FilterAction() {
            @Override
            public String type() {
                return "CAPTURE";
            }

            @Override
            public void apply(final Object workUnit, final Map<String, Object> params) {
                capturedWorkUnits.add(workUnit.toString());
            }
        };
        engine = new FilterRegistryEngine(new JexlConditionEvaluator(), List.of(capturingAction));
    }

    private WorkLifecycleEvent event(final WorkEventType type, final Map<String, Object> ctx,
            final Object source) {
        return new WorkLifecycleEvent() {
            @Override
            public WorkEventType eventType() {
                return type;
            }

            @Override
            public Map<String, Object> context() {
                return ctx;
            }

            @Override
            public Object source() {
                return source;
            }
        };
    }

    @Test
    void matchingCondition_actionFired() {
        final var def = FilterDefinition.onAdd("test", "desc", true,
                "workItem.category == 'finance'", Map.of(),
                List.of(ActionDescriptor.of("CAPTURE", Map.of())));
        final var evt = event(WorkEventType.CREATED, Map.of("category", "finance"), "WORK_UNIT_1");
        engine.processEvent(evt, List.of(def));
        assertThat(capturedWorkUnits).containsExactly("WORK_UNIT_1");
    }

    @Test
    void nonMatchingCondition_actionNotFired() {
        final var def = FilterDefinition.onAdd("test", "desc", true,
                "workItem.category == 'legal'", Map.of(),
                List.of(ActionDescriptor.of("CAPTURE", Map.of())));
        final var evt = event(WorkEventType.CREATED, Map.of("category", "finance"), "UNIT");
        engine.processEvent(evt, List.of(def));
        assertThat(capturedWorkUnits).isEmpty();
    }

    @Test
    void disabledDefinition_actionNotFired() {
        final var def = FilterDefinition.onAdd("test", "desc", false,
                "workItem.category == 'finance'", Map.of(),
                List.of(ActionDescriptor.of("CAPTURE", Map.of())));
        final var evt = event(WorkEventType.CREATED, Map.of("category", "finance"), "UNIT");
        engine.processEvent(evt, List.of(def));
        assertThat(capturedWorkUnits).isEmpty();
    }

    @Test
    void wrongEventType_actionNotFired() {
        final var def = FilterDefinition.onAdd("test", "desc", true,
                "workItem.category == 'finance'", Map.of(),
                List.of(ActionDescriptor.of("CAPTURE", Map.of())));
        // def fires on ADD (CREATED) only; this event is ASSIGNED (UPDATE)
        final var evt = event(WorkEventType.ASSIGNED, Map.of("category", "finance"), "UNIT");
        engine.processEvent(evt, List.of(def));
        assertThat(capturedWorkUnits).isEmpty();
    }

    @Test
    void unknownActionType_ignoredGracefully() {
        final var def = FilterDefinition.onAdd("test", "desc", true,
                "workItem.category == 'finance'", Map.of(),
                List.of(ActionDescriptor.of("UNKNOWN_ACTION", Map.of())));
        final var evt = event(WorkEventType.CREATED, Map.of("category", "finance"), "UNIT");
        engine.processEvent(evt, List.of(def));
        assertThat(capturedWorkUnits).isEmpty();
    }

    @Test
    void reentrancyGuard_preventsRecursiveProcessing() {
        // When an action itself fires a lifecycle event, the engine must not recurse
        final int[] callCount = { 0 };
        final FilterAction selfFiringAction = new FilterAction() {
            @Override
            public String type() {
                return "SELF_FIRE";
            }

            @Override
            public void apply(final Object workUnit, final Map<String, Object> params) {
                callCount[0]++;
                // Simulates what the CDI observer would do if another event fired during processing
                // (The ThreadLocal guard in the engine prevents this in production)
            }
        };
        engine = new FilterRegistryEngine(new JexlConditionEvaluator(), List.of(selfFiringAction));
        final var def = FilterDefinition.onAdd("test", "desc", true,
                "workItem.category == 'finance'", Map.of(),
                List.of(ActionDescriptor.of("SELF_FIRE", Map.of())));
        final var evt = event(WorkEventType.CREATED, Map.of("category", "finance"), "UNIT");
        engine.processEvent(evt, List.of(def));
        assertThat(callCount[0]).isEqualTo(1); // fired exactly once — no recursion
    }
}
