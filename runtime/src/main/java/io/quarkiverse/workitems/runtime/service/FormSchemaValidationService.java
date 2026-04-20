package io.quarkiverse.workitems.runtime.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

/**
 * Pure JSON Schema validation service.
 *
 * <p>
 * Validates a JSON string against a JSON Schema draft-07 definition.
 * Contains no database access — callers resolve the schema string before calling here.
 * This design makes the validation logic unit-testable without {@code @QuarkusTest}.
 *
 * <p>
 * Null or blank JSON is treated as "no data to validate" — returns an empty list
 * (no violations). This allows optional payload/resolution fields on WorkItem to remain
 * optional even when a form schema exists for the category.
 *
 * @see <a href="https://github.com/mdproctor/quarkus-workitems/issues/108">Issue #108</a>
 * @see <a href="https://github.com/mdproctor/quarkus-workitems/issues/98">Epic #98</a>
 */
@ApplicationScoped
public class FormSchemaValidationService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final JsonSchemaFactory FACTORY = JsonSchemaFactory
            .getInstance(SpecVersion.VersionFlag.V7);

    /**
     * Validate {@code json} against the given JSON Schema draft-07 definition.
     *
     * @param schemaDefinition the JSON Schema as a string (draft-07)
     * @param json the JSON value to validate; null or blank → no violations returned
     * @return list of violation messages; empty means valid (or no data to validate)
     */
    public List<String> validate(final String schemaDefinition, final String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }

        try {
            final JsonSchema schema = FACTORY.getSchema(schemaDefinition);
            final JsonNode node = MAPPER.readTree(json);
            final Set<ValidationMessage> messages = schema.validate(node);

            if (messages.isEmpty()) {
                return List.of();
            }

            final List<String> violations = new ArrayList<>(messages.size());
            for (final ValidationMessage msg : messages) {
                violations.add(msg.getMessage());
            }
            return violations;
        } catch (final Exception e) {
            return List.of("Invalid JSON: " + e.getMessage());
        }
    }
}
