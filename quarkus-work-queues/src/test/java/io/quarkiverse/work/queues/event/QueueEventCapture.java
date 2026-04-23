package io.quarkiverse.work.queues.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * Test-only CDI observer that captures {@link WorkItemQueueEvent} instances for assertion.
 * Injected into test classes; reset via {@link #clear()} in {@code @BeforeEach}.
 */
@ApplicationScoped
public class QueueEventCapture {

    private final List<WorkItemQueueEvent> events = new CopyOnWriteArrayList<>();

    public void onEvent(@Observes final WorkItemQueueEvent event) {
        events.add(event);
    }

    public List<WorkItemQueueEvent> events() {
        return List.copyOf(events);
    }

    public List<WorkItemQueueEvent> eventsOfType(final QueueEventType type) {
        return events.stream().filter(e -> e.eventType() == type).toList();
    }

    public void clear() {
        events.clear();
    }
}
