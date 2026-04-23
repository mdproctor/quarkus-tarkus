package io.quarkiverse.work.queues.model;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

/**
 * Tracks the inverse index: which WorkItems a given filter has applied INFERRED labels to.
 * Used for O(affected) cascade when a filter is deleted.
 */
@Entity
@Table(name = "filter_chain")
public class FilterChain extends PanacheEntityBase {

    /** Primary key — UUID assigned on first persist. */
    @Id
    public UUID id;

    /** The filter whose label applications this chain tracks. */
    @Column(name = "filter_id", nullable = false)
    public UUID filterId;

    /** WorkItem IDs to which this filter has applied at least one INFERRED label. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "filter_chain_work_item", joinColumns = @JoinColumn(name = "filter_chain_id"))
    @Column(name = "work_item_id")
    public Set<UUID> workItems = new HashSet<>();

    /** Assigns a UUID primary key before first insert. */
    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }

    /**
     * Finds the FilterChain for the given filterId, creating and persisting one if absent.
     *
     * @param filterId the filter whose chain to look up or create
     * @return the existing or newly created FilterChain; never null
     */
    public static FilterChain findOrCreateForFilter(final UUID filterId) {
        return FilterChain.<FilterChain> find("filterId", filterId)
                .firstResultOptional()
                .orElseGet(() -> {
                    var fc = new FilterChain();
                    fc.filterId = filterId;
                    fc.persist();
                    return fc;
                });
    }

    /**
     * Finds the FilterChain for the given filterId, returning null if absent.
     *
     * @param filterId the filter to look up
     * @return the FilterChain, or null if none exists
     */
    public static FilterChain findByFilterId(final UUID filterId) {
        return FilterChain.<FilterChain> find("filterId", filterId).firstResult();
    }
}
