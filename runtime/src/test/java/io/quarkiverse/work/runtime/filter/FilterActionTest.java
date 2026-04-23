package io.quarkiverse.work.runtime.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

class FilterActionTest {

    @Test
    void apply_receivesOpaqueWorkUnit() {
        final Object[] captured = new Object[1];
        final FilterAction action = new FilterAction() {
            @Override
            public String type() {
                return "TEST";
            }

            @Override
            public void apply(final Object workUnit, final Map<String, Object> params) {
                captured[0] = workUnit;
            }
        };
        final Object domain = new Object();
        action.apply(domain, Map.of());
        assertThat(captured[0]).isSameAs(domain);
    }

    @Test
    void type_returnsActionIdentifier() {
        final FilterAction action = new FilterAction() {
            @Override
            public String type() {
                return "MY_ACTION";
            }

            @Override
            public void apply(Object workUnit, Map<String, Object> params) {
            }
        };
        assertThat(action.type()).isEqualTo("MY_ACTION");
    }
}
