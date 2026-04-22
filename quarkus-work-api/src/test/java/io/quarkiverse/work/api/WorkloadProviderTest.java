package io.quarkiverse.work.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WorkloadProviderTest {

    @Test
    void canImplementWithLambda() {
        WorkloadProvider p = workerId -> workerId.equals("alice") ? 3 : 0;
        assertThat(p.getActiveWorkCount("alice")).isEqualTo(3);
        assertThat(p.getActiveWorkCount("bob")).isZero();
    }

    @Test
    void zeroForUnknownWorker() {
        WorkloadProvider p = workerId -> 0;
        assertThat(p.getActiveWorkCount("unknown")).isZero();
    }
}
