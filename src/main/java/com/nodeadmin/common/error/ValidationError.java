package com.nodeadmin.common.error;

import java.util.Map;

/**
 * Thrown when input data fails business-level validation (HTTP 422).
 * Stores per-field error messages in {@code fieldErrors}.
 */
public class ValidationError extends AppError {

    private final Map<String, String> fieldErrors;

    public ValidationError(String message, Map<String, String> fieldErrors) {
        super(422, "VALIDATION_ERROR", message);
        this.fieldErrors = fieldErrors;
    }

    public ValidationError(String message) {
        this(message, Map.of());
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
