package io.casehub.work.testing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import io.casehub.work.runtime.model.WorkItemNote;
import io.casehub.work.runtime.repository.WorkItemNoteStore;

/**
 * In-memory implementation of {@link WorkItemNoteStore} for use in tests.
 * No datasource or Flyway configuration required.
 *
 * <p>
 * Activate by including {@code quarkus-work-testing} on the test classpath.
 * Call {@link #clear()} in {@code @BeforeEach} to isolate tests.
 */
@ApplicationScoped
@Alternative
@Priority(1)
public class InMemoryWorkItemNoteStore implements WorkItemNoteStore {

    private final Map<UUID, WorkItemNote> store = new LinkedHashMap<>();

    /** Clears all stored notes. Call in {@code @BeforeEach} to isolate tests. */
    public void clear() {
        store.clear();
    }

    @Override
    public WorkItemNote append(final WorkItemNote note) {
        if (note.id == null) {
            note.id = UUID.randomUUID();
        }
        if (note.createdAt == null) {
            note.createdAt = java.time.Instant.now();
        }
        store.put(note.id, note);
        return note;
    }

    @Override
    public Optional<WorkItemNote> findById(final UUID noteId) {
        return Optional.ofNullable(store.get(noteId));
    }

    @Override
    public List<WorkItemNote> findByWorkItemId(final UUID workItemId) {
        return store.values().stream()
                .filter(n -> workItemId.equals(n.workItemId))
                .sorted(java.util.Comparator.comparing(n -> n.createdAt))
                .toList();
    }

    @Override
    public WorkItemNote update(final WorkItemNote note) {
        store.put(note.id, note);
        return note;
    }

    @Override
    public boolean delete(final UUID noteId) {
        return store.remove(noteId) != null;
    }

    /** Returns all notes, for test inspection. */
    public List<WorkItemNote> findAll() {
        return new ArrayList<>(store.values());
    }
}
