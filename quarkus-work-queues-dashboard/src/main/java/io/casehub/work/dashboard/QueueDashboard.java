package io.casehub.work.dashboard;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.tui.TuiRunner;
import dev.tamboui.tui.event.Event;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.block.Title;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import dev.tamboui.widgets.table.TableState;
import io.casehub.work.runtime.event.WorkItemLifecycleEvent;
import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.repository.WorkItemQuery;
import io.casehub.work.runtime.repository.WorkItemStore;
import io.quarkus.narayana.jta.QuarkusTransaction;

/**
 * Tamboui TUI dashboard running inside Quarkus.
 *
 * <p>
 * Renders a two-panel terminal UI:
 * <ul>
 * <li>Top panel: 3×3 queue board (tiers × states)</li>
 * <li>Bottom panel: timestamped console log (last 8 events)</li>
 * </ul>
 *
 * <p>
 * Keybindings:
 * <ul>
 * <li>'s' — advance the document review scenario one step</li>
 * <li>'r' — reset the scenario</li>
 * <li>'q' — quit</li>
 * </ul>
 */
@ApplicationScoped
public class QueueDashboard {

    private static final int LOG_CAPACITY = 8;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Inject
    WorkItemStore workItemStore;

    @Inject
    ReviewStepService stepService;

    private final AtomicReference<List<WorkItem>> latestItems = new AtomicReference<>(List.of());
    private final AtomicReference<Deque<String>> logLines = new AtomicReference<>(new ArrayDeque<>());

    /**
     * Called by Quarkus CDI when a WorkItem lifecycle transition occurs.
     * Refreshes the item list so the board repaints on the next frame.
     */
    @Transactional
    public void onLifecycleEvent(@ObservesAsync final WorkItemLifecycleEvent event) {
        final List<WorkItem> items = workItemStore.scan(WorkItemQuery.all());
        latestItems.set(List.copyOf(items));
        addLog("Event: " + event.type() + " — " + event.workItemId());
    }

    private void addLog(final String message) {
        logLines.updateAndGet(deque -> {
            final Deque<String> copy = new ArrayDeque<>(deque);
            copy.addFirst("[" + LocalTime.now().format(TIME_FMT) + "] " + message);
            while (copy.size() > LOG_CAPACITY) {
                copy.removeLast();
            }
            return copy;
        });
    }

    /** Entry point called from {@link DashboardMain#run}. Blocks until the user quits. */
    public void start() throws Exception {
        refreshItems();
        addLog("Quarkus WorkItems Queue Dashboard started");
        addLog("Lambda filters: " + stepService.lambdaFilterNames());
        addLog(stepService.nextAction());

        try (final var tui = TuiRunner.create()) {
            tui.run(this::handleEvent, frame -> renderBoard(frame));
        }
    }

    /** Package-private so Pilot tests can pass this as EventHandler to TuiTestRunner. */
    boolean handleEvent(final Event event, final TuiRunner runner) {
        if (event instanceof KeyEvent k) {
            if (k.isQuit()) {
                runner.quit();
                return true;
            }
            if (k.isCharIgnoreCase('s')) {
                advanceStep();
                return true;
            }
            if (k.isCharIgnoreCase('r')) {
                resetScenario();
                return true;
            }
        }
        return false;
    }

    private void advanceStep() {
        try {
            final ReviewStepService.StepResult result = QuarkusTransaction.requiringNew()
                    .call(stepService::advance);
            refreshItems();
            addLog(result.action() + ": " + result.detail());
            result.hints().forEach(h -> addLog("  \u2192 " + h));
            addLog(stepService.nextAction());
        } catch (final Exception e) {
            addLog("Error: " + e.getMessage());
        }
    }

    private void resetScenario() {
        try {
            final ReviewStepService.StepResult result = QuarkusTransaction.requiringNew()
                    .call(stepService::reset);
            refreshItems();
            addLog(result.action() + ": " + result.detail());
            addLog(stepService.nextAction());
        } catch (final Exception e) {
            addLog("Error resetting: " + e.getMessage());
        }
    }

    private void refreshItems() {
        try {
            final List<WorkItem> items = QuarkusTransaction.requiringNew()
                    .call(() -> workItemStore.scan(WorkItemQuery.all()));
            latestItems.set(List.copyOf(items));
        } catch (final Exception e) {
            addLog("Refresh error: " + e.getMessage());
        }
    }

    /** Package-private so Pilot tests can pass this as Renderer to TuiTestRunner. */
    void renderBoard(final dev.tamboui.terminal.Frame frame) {
        final List<WorkItem> items = latestItems.get();
        final Rect area = frame.area();

        // Build the queue grid: tier -> state -> list of titles
        final Map<String, Map<String, List<String>>> grid = QueueBoardBuilder.build(items);

        // Build table rows
        final List<Row> rows = new ArrayList<>();
        for (int i = 0; i < QueueBoardBuilder.TIERS.length; i++) {
            final String tier = QueueBoardBuilder.TIERS[i];
            final String tierLabel = QueueBoardBuilder.TIER_LABELS[i];
            final Map<String, List<String>> tierData = grid.getOrDefault(tier, Map.of());

            rows.add(Row.from(
                    Cell.from(tierLabel),
                    Cell.from(QueueBoardBuilder.formatCell(tierData.getOrDefault("unassigned", List.of()))),
                    Cell.from(QueueBoardBuilder.formatCell(tierData.getOrDefault("claimed", List.of()))),
                    Cell.from(QueueBoardBuilder.formatCell(tierData.getOrDefault("active", List.of())))));
        }

        final var boardBlock = Block.builder()
                .title(Title.from(" Document Review Queue Board "))
                .borders(Borders.ALL)
                .build();

        final var table = Table.builder()
                .block(boardBlock)
                .header(Row.from("Tier", "Unassigned (PENDING)", "Claimed (ASSIGNED)", "Active (IN_PROGRESS)"))
                .rows(rows)
                .widths(Constraint.length(12), Constraint.fill(), Constraint.fill(), Constraint.fill())
                .build();

        // Build log panel content
        final String logContent = String.join("\n", logLines.get());
        final var logBlock = Block.builder()
                .title(Title.from(" Console "))
                .borders(Borders.ALL)
                .build();
        final var logParagraph = Paragraph.builder()
                .block(logBlock)
                .text(dev.tamboui.text.Text.from(logContent))
                .build();

        // Split vertically: queue board fills top, log panel fills bottom
        final List<Rect> sections = Layout.vertical()
                .constraints(Constraint.fill(), Constraint.length(LOG_CAPACITY + 2))
                .split(area);

        final Rect tableArea = sections.get(0);
        final Rect logArea = sections.get(1);

        final var tableState = new TableState();
        frame.renderStatefulWidget(table, tableArea, tableState);
        frame.renderWidget(logParagraph, logArea);
    }
}
