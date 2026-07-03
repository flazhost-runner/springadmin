package com.nodeadmin.common.error;

/**
 * Thrown when a write operation conflicts with existing data (HTTP 409).
 */
public class ConflictError extends AppError {

    public ConflictError(String message) {
        super(409, "CONFLICT", message);
    }

    public ConflictError(String message, Throwable cause) {
        super(409, "CONFLICT", message, cause);
    }
}
