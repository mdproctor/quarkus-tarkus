package io.casehub.work.runtime.repository.jpa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.work.runtime.model.WorkItemNote;
import io.casehub.work.runtime.repository.WorkItemNoteStore;

/**
 * Default JPA/Panache implementation of {@link WorkItemNoteStore}.
 */
@ApplicationScoped
public class JpaWorkItemNoteStore implements WorkItemNoteStore {

    @Override
    public WorkItemNote append(final WorkItemNote note) {
        note.persistAndFlush();
        return note;
    }

    @Override
    public Optional<WorkItemNote> findById(final UUID noteId) {
        return Optional.ofNullable(WorkItemNote.findById(noteId));
    }

    @Override
    public List<WorkItemNote> findByWorkItemId(final UUID workItemId) {
        return WorkItemNote.list("workItemId = ?1 ORDER BY createdAt ASC", workItemId);
    }

    @Override
    public WorkItemNote update(final WorkItemNote note) {
        note.persistAndFlush();
        return note;
    }

    @Override
    public boolean delete(final UUID noteId) {
        return WorkItemNote.deleteById(noteId);
    }
}
