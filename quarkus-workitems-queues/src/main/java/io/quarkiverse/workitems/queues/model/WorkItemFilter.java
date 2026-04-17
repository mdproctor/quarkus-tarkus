package io.quarkiverse.workitems.queues.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.workitems.queues.service.ExpressionDescriptor;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

@Entity
@Table(name = "work_item_filter")
public class WorkItemFilter extends PanacheEntityBase {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Id
    public UUID id;

    @Column(nullable = false, length = 255)
    public String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    public FilterScope scope;

    @Column(name = "owner_id", length = 255)
    public String ownerId;

    @Column(name = "condition_language", nullable = false, length = 20)
    public String conditionLanguage;

    @Column(name = "condition_expression", length = 4000)
    public String conditionExpression;

    @Column(length = 4000)
    public String actions;

    public boolean active = true;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }

    public List<FilterAction> parseActions() {
        if (actions == null || actions.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(actions, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    public static String serializeActions(final List<FilterAction> list) {
        try {
            return MAPPER.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    /**
     * Returns an {@link ExpressionDescriptor} bundling this filter's language and expression.
     * Preferred over accessing {@code conditionLanguage} and {@code conditionExpression} separately.
     */
    public ExpressionDescriptor conditionDescriptor() {
        return ExpressionDescriptor.of(conditionLanguage, conditionExpression);
    }

    public static List<WorkItemFilter> findActive() {
        return find("active = true AND conditionLanguage != 'lambda'").list();
    }
}
