package io.quarkiverse.work.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

class EscalationPolicyTest {

    @Test
    void canImplementWithLambdaAndInspectEventType() {
        final WorkEventType[] captured = new WorkEventType[1];
        EscalationPolicy policy = event -> captured[0] = event.eventType();

        var event = new WorkLifecycleEvent() {
            @Override
            public WorkEventType eventType() {
                return WorkEventType.EXPIRED;
            }

            @Override
            public Map<String, Object> context() {
                return Map.of();
            }

            @Override
            public Object source() {
                return "work-unit";
            }
        };

        policy.escalate(event);
        assertThat(captured[0]).isEqualTo(WorkEventType.EXPIRED);
    }

    @Test
    void distinguishesExpiredFromClaimExpired() {
        final WorkEventType[] captured = new WorkEventType[1];
        EscalationPolicy policy = event -> captured[0] = event.eventType();

        var claimEvent = new WorkLifecycleEvent() {
            @Override
            public WorkEventType eventType() {
                return WorkEventType.CLAIM_EXPIRED;
            }

            @Override
            public Map<String, Object> context() {
                return Map.of();
            }

            @Override
            public Object source() {
                return "work-unit";
            }
        };

        policy.escalate(claimEvent);
        assertThat(captured[0]).isEqualTo(WorkEventType.CLAIM_EXPIRED);
    }
}
