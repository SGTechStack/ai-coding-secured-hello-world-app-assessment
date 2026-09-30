package local.builderday.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One entry of the {@code errors} extension on a rejected submission's problem detail (ADR 0001). Carries stable codes
 * only; submitted values are never echoed. {@code field} is omitted when the rule is not tied to a single field.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FieldErrorResponse(String field, String code) {}
