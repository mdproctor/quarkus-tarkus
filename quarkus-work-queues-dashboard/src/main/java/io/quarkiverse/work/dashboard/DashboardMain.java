package io.quarkiverse.work.dashboard;

import jakarta.inject.Inject;

import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;

/**
 * {@code @QuarkusMain} entry point. Quarkus is fully started before {@code run()} is called.
 * {@code TuiRunner.run()} blocks here — this is the designated application main for CLI mode.
 * Quarkus shuts down cleanly when {@code run()} returns (user presses 'q').
 */
@QuarkusMain
public class DashboardMain implements QuarkusApplication {

    @Inject
    QueueDashboard dashboard;

    @Override
    public int run(final String... args) throws Exception {
        dashboard.start();
        return 0;
    }
}
