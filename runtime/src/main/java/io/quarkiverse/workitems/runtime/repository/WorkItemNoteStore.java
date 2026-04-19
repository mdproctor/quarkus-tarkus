package io.quarkiverse.workitems.runtime.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.quarkiverse.workitems.runtime.model.WorkItemNote;

/**
 * SPI for persisting {@link WorkItemNote} records.
 *
 * <p>
 * The default implementation uses JPA/Panache. Alternatives (MongoDB, in-memory)
 * override via CDI {@code @Alternative @Priority(1)}, consistent with the rest of
 * the WorkItems storage layer.
 */
public interface WorkItemNoteStore {

    /**
     * Persist a new note.
     *
     * @param note the note to persist; must not be {@code null}
     * @return the persisted note with {@code id} and {@code createdAt} populated
     */
    WorkItemNote append(WorkItemNote note);

    /**
     * Find a note by its primary key.
     *
     * @param noteId the UUID primary key
     * @return the note, or empty if not found
     */
    Optional<WorkItemNote> findById(UUID noteId);

    /**
     * Return all notes for a WorkItem, ordered chronologically (oldest first).
     *
     * @param workItemId the WorkItem UUID
     * @return list of notes; may be empty, never null
     */
    List<WorkItemNote> findByWorkItemId(UUID workItemId);

    /**
     * Persist an edited note.
     *
     * @param note the note with updated {@code content} and {@code editedAt}
     * @return the updated note
     */
    WorkItemNote update(WorkItemNote note);

    /**
     * Delete a note by its primary key.
     *
     * @param noteId the UUID primary key
     * @return {@code true} if the note existed and was deleted, {@code false} if not found
     */
    boolean delete(UUID noteId);
}
