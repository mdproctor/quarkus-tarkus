package io.quarkiverse.work.runtime.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for FormSchemaValidationService — pure logic, no Quarkus boot.
 *
 * <p>
 * Tests the schema validation logic directly, independent of the REST layer
 * or database. Uses JSON Schema draft-07.
 *
 * <p>
 * Issue #108, Epic #98.
 */
class FormSchemaValidationServiceTest {

    private final FormSchemaValidationService validator = new FormSchemaValidationService();

    private static final String LOAN_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "loanAmount": {"type": "number", "minimum": 1},
                "purpose":    {"type": "string"}
              },
              "required": ["loanAmount"]
            }
            """;

    private static final String APPROVAL_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "approved": {"type": "boolean"},
                "reason":   {"type": "string"}
              },
              "required": ["approved"]
            }
            """;

    // ── validate(schema, json) ────────────────────────────────────────────────

    @Test
    void validate_returnsEmpty_whenJsonSatisfiesSchema() {
        final List<String> violations = validator.validate(LOAN_SCHEMA,
                "{\"loanAmount\": 5000, \"purpose\": \"home\"}");
        assertThat(violations).isEmpty();
    }

    @Test
    void validate_returnsViolations_whenRequiredFieldMissing() {
        final List<String> violations = validator.validate(LOAN_SCHEMA,
                "{\"purpose\": \"home\"}");
        assertThat(violations).isNotEmpty();
        assertThat(violations.get(0)).containsIgnoringCase("loanAmount");
    }

    @Test
    void validate_returnsViolations_whenFieldHasWrongType() {
        final List<String> violations = validator.validate(LOAN_SCHEMA,
                "{\"loanAmount\": \"not-a-number\"}");
        assertThat(violations).isNotEmpty();
    }

    @Test
    void validate_returnsViolations_whenMinimumViolated() {
        final List<String> violations = validator.validate(LOAN_SCHEMA,
                "{\"loanAmount\": 0}");
        assertThat(violations).isNotEmpty();
    }

    @Test
    void validate_returnsEmpty_whenOptionalFieldsAbsent() {
        final List<String> violations = validator.validate(LOAN_SCHEMA,
                "{\"loanAmount\": 100}");
        assertThat(violations).isEmpty();
    }

    @Test
    void validate_handlesResolutionSchema_validPayload() {
        final List<String> violations = validator.validate(APPROVAL_SCHEMA,
                "{\"approved\": true, \"reason\": \"Looks good\"}");
        assertThat(violations).isEmpty();
    }

    @Test
    void validate_handlesResolutionSchema_missingRequired() {
        final List<String> violations = validator.validate(APPROVAL_SCHEMA,
                "{\"reason\": \"no decision\"}");
        assertThat(violations).isNotEmpty();
        assertThat(violations.get(0)).containsIgnoringCase("approved");
    }

    @Test
    void validate_handlesResolutionSchema_wrongBooleanType() {
        final List<String> violations = validator.validate(APPROVAL_SCHEMA,
                "{\"approved\": \"yes\"}");
        assertThat(violations).isNotEmpty();
    }

    @Test
    void validate_returnsEmpty_whenJsonIsNull() {
        // Null payload/resolution = no data to validate; skip validation.
        assertThat(validator.validate(LOAN_SCHEMA, null)).isEmpty();
    }

    @Test
    void validate_returnsEmpty_whenJsonIsBlank() {
        assertThat(validator.validate(LOAN_SCHEMA, "")).isEmpty();
    }

    @Test
    void validate_handlesNestedObjectSchema() {
        final String nestedSchema = """
                {
                  "type": "object",
                  "properties": {
                    "applicant": {
                      "type": "object",
                      "properties": {
                        "name": {"type": "string"}
                      },
                      "required": ["name"]
                    }
                  },
                  "required": ["applicant"]
                }
                """;

        assertThat(validator.validate(nestedSchema, "{\"applicant\":{\"name\":\"Alice\"}}")).isEmpty();
        assertThat(validator.validate(nestedSchema, "{\"applicant\":{}}")).isNotEmpty();
    }

    @Test
    void validate_handlesArraySchema() {
        final String arraySchema = """
                {
                  "type": "object",
                  "properties": {
                    "tags": {"type": "array", "items": {"type": "string"}}
                  }
                }
                """;

        assertThat(validator.validate(arraySchema, "{\"tags\":[\"a\",\"b\"]}")).isEmpty();
        assertThat(validator.validate(arraySchema, "{\"tags\":[1,2]}")).isNotEmpty();
    }
}
