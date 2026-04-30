package io.casehub.work.testing;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import io.casehub.work.runtime.model.AuditEntry;
import io.casehub.work.runtime.repository.AuditEntryStore;
import io.casehub.work.runtime.repository.AuditQuery;

/**
 * In-memory implementation of {@link AuditEntryStore} for use in tests of
 * applications that embed CaseHub Work. No datasource or Flyway configuration
 * is required.
 *
 * <p>
 * Activate by including {@code quarkus-work-testing} on the test classpath. CDI
 * selects this bean over the default Panache implementation via {@code @Alternative}
 * and {@code @Priority(1)}.
 *
 * <p>
 * <strong>Not thread-safe</strong> — designed for single-threaded test use only.
 *
 * <p>
 * Call {@link #clear()} in a {@code @BeforeEach} method to isolate tests from one
 * another.
 */
@ApplicationScoped
@Alternative
@Priority(1)
public class InMemoryAuditEntryStore implements AuditEntryStore {

    // NOT thread-safe — designed for single-threaded test use
    private final List<AuditEntry> entries = new ArrayList<>();

    /**
     * Clears all stored entries. Call in {@code @BeforeEach} to isolate tests.
     */
    public void clear() {
        entries.clear();
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * If {@code entry.id} is {@code null} a fresh {@link UUID} is assigned. If
     * {@code entry.occurredAt} is {@code null} it is set to {@link Instant#now()}.
     */
    @Override
    public void append(final AuditEntry entry) {
        if (entry.id == null) {
            entry.id = UUID.randomUUID();
        }
        if (entry.occurredAt == null) {
            entry.occurredAt = Instant.now();
        }
        entries.add(entry);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<AuditEntry> findByWorkItemId(final UUID workItemId) {
        return entries.stream()
                .filter(e -> workItemId.equals(e.workItemId))
                .sorted(Comparator.comparing(e -> e.occurredAt))
                .toList();
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * In-memory implementation for tests — applies actorId, event, from, to filters only.
     * Category filter is not supported (no WorkItem access); it is silently ignored.
     */
    @Override
    public List<AuditEntry> query(final AuditQuery query) {
        return entries.stream()
                .filter(e -> query.actorId() == null || query.actorId().equals(e.actor))
                .filter(e -> query.event() == null || query.event().equals(e.event))
                .filter(e -> query.from() == null || !e.occurredAt.isBefore(query.from()))
                .filter(e -> query.to() == null || !e.occurredAt.isAfter(query.to()))
                .sorted(Comparator.comparing((AuditEntry e) -> e.occurredAt).reversed())
                .skip((long) query.page() * query.size())
                .limit(query.size())
                .toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long count(final AuditQuery query) {
        return entries.stream()
                .filter(e -> query.actorId() == null || query.actorId().equals(e.actor))
                .filter(e -> query.event() == null || query.event().equals(e.event))
                .filter(e -> query.from() == null || !e.occurredAt.isBefore(query.from()))
                .filter(e -> query.to() == null || !e.occurredAt.isAfter(query.to()))
                .count();
    }
}
